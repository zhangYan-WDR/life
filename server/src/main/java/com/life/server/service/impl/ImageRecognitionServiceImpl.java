package com.life.server.service.impl;

import com.aliyun.ocr_api20210707.Client;
import com.aliyun.ocr_api20210707.models.RecognizeAllTextRequest;
import com.aliyun.ocr_api20210707.models.RecognizeAllTextResponse;
import com.aliyun.ocr_api20210707.models.RecognizeAllTextResponseBody;
import com.aliyun.tea.TeaException;
import com.aliyun.teautil.models.RuntimeOptions;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.life.server.common.BizException;
import com.life.server.config.AppProperties;
import com.life.server.dto.response.RecipeImageRecognitionResponse;
import com.life.server.dto.response.ReceiptImageRecognitionResponse;
import com.life.server.dto.response.ReceiptRecognizedItemResponse;
import com.life.server.service.ImageRecognitionService;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ImageRecognitionServiceImpl implements ImageRecognitionService {

    private static final Logger log = LoggerFactory.getLogger(ImageRecognitionServiceImpl.class);

    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    public ImageRecognitionServiceImpl(AppProperties appProperties,
                                       ObjectMapper objectMapper) {
        this.appProperties = appProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public ReceiptImageRecognitionResponse analyzeReceipt(byte[] imageBytes, String fileName) {
        validateImage(fileName, imageBytes);
        OcrPayload payload = recognizeText(imageBytes);
        List<String> lines = buildOrderedLines(payload);
        List<ReceiptRecognizedItemResponse> items = parseReceiptItems(lines);
        if (items.isEmpty()) {
            items = parseReceiptItemsFromRawText(payload.content);
        }
        if (items.isEmpty()) {
            throw new BizException("没有从小票里识别出可导入的食材，请换一张更清晰的小票试试");
        }
        return ReceiptImageRecognitionResponse.builder()
            .rawText(payload.content)
            .lines(lines)
            .items(items)
            .build();
    }

    @Override
    public RecipeImageRecognitionResponse analyzeRecipeImage(byte[] imageBytes, String fileName) {
        validateImage(fileName, imageBytes);
        OcrPayload payload = recognizeText(imageBytes);
        List<String> lines = buildOrderedLines(payload);
        if (lines.isEmpty() && !StringUtils.hasText(payload.content)) {
            throw new BizException("没有识别出文字，请换一张更清晰的菜谱图试试");
        }
        String name = parseRecipeName(lines);
        List<String> ingredientNames = parseRecipeIngredientNames(lines);
        String instructions = parseRecipeInstructions(lines, payload.content);
        String note = ingredientNames.isEmpty() ? "图片识别已导入原文，请检查后再保存。" : "识别到的食材会展示在编辑页里，记得手动映射到食材目录。";
        return RecipeImageRecognitionResponse.builder()
            .name(name)
            .instructions(instructions)
            .note(note)
            .ingredientNames(ingredientNames)
            .rawText(payload.content)
            .lines(lines)
            .build();
    }

    private OcrPayload recognizeText(byte[] imageBytes) {
        AppProperties.Ocr ocr = requireOcrConfig();
        try {
            Client client = createClient(ocr);
            RecognizeAllTextRequest request = new RecognizeAllTextRequest()
                .setType("Advanced")
                .setBody(new ByteArrayInputStream(imageBytes));
            RuntimeOptions runtime = new RuntimeOptions();
            RecognizeAllTextResponse response = client.recognizeAllTextWithOptions(request, runtime);
            RecognizeAllTextResponseBody body = response.getBody();
            if (body == null) {
                throw new BizException("OCR 返回了空响应，请检查 OCR 服务是否已开通、AK/SK 是否有权限");
            }
            if (StringUtils.hasText(body.getMessage()) && !"200".equals(body.getCode())) {
                throw new BizException(body.getMessage());
            }
            return parseOcrPayload(body.getData());
        } catch (TeaException ex) {
            log.warn("OCR sdk error. message={}, data={}", ex.getMessage(), ex.getData());
            Object recommend = ex.getData() == null ? null : ex.getData().get("Recommend");
            String message = StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : "调用 OCR 识别失败";
            if (recommend != null) {
                message = message + "，诊断信息：" + recommend;
            }
            throw new BizException(message);
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("OCR sdk request failed", ex);
            throw new BizException("调用 OCR 识别失败：" + (StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : ex.getClass().getSimpleName()));
        }
    }

    private OcrPayload parseOcrPayload(Object data) {
        try {
            if (data == null) {
                throw new BizException("OCR 没有返回识别结果");
            }
            JsonNode dataNode = objectMapper.valueToTree(data);
            List<OcrWord> words = new ArrayList<OcrWord>();
            JsonNode wordsNode = dataNode.path("prism_wordsInfo");
            if (wordsNode.isArray()) {
                for (JsonNode item : wordsNode) {
                    words.add(new OcrWord(
                        item.path("word").asText(""),
                        item.path("x").asInt(0),
                        item.path("y").asInt(0),
                        item.path("width").asInt(0),
                        item.path("height").asInt(0)
                    ));
                }
            }
            if (words.isEmpty()) {
                appendRecognizeAllTextWords(dataNode.path("SubImages"), words);
            }
            String content = readText(dataNode, "Content", "content");
            if (!StringUtils.hasText(content) && dataNode.path("SubImages").isArray()) {
                content = buildContentFromSubImages(dataNode.path("SubImages"));
            }
            return new OcrPayload(content, words);
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException("OCR 结果解析失败");
        }
    }

    private void appendRecognizeAllTextWords(JsonNode subImagesNode, List<OcrWord> words) {
        if (!subImagesNode.isArray()) {
            return;
        }
        for (JsonNode subImage : subImagesNode) {
            JsonNode blockDetails = subImage.path("BlockInfo").path("BlockDetails");
            if (!blockDetails.isArray()) {
                continue;
            }
            for (JsonNode block : blockDetails) {
                String word = readText(block, "BlockContent", "Word", "word");
                JsonNode rect = block.path("BlockRect");
                int centerX = rect.path("CenterX").asInt(block.path("x").asInt(0));
                int centerY = rect.path("CenterY").asInt(block.path("y").asInt(0));
                int width = rect.path("Width").asInt(block.path("width").asInt(0));
                int height = rect.path("Height").asInt(block.path("height").asInt(0));
                int x = centerX;
                int y = centerY;
                if (width > 0) {
                    x = centerX - (width / 2);
                }
                if (height > 0) {
                    y = centerY - (height / 2);
                }
                words.add(new OcrWord(word, x, y, width, height));
            }
        }
    }

    private String buildContentFromSubImages(JsonNode subImagesNode) {
        List<String> parts = new ArrayList<String>();
        for (JsonNode subImage : subImagesNode) {
            String subContent = readText(subImage, "Content", "SubImageContent");
            if (!StringUtils.hasText(subContent)) {
                JsonNode rowDetails = subImage.path("RowInfo").path("RowDetails");
                if (rowDetails.isArray()) {
                    for (JsonNode row : rowDetails) {
                        String rowContent = readText(row, "RowContent");
                        if (StringUtils.hasText(rowContent)) {
                            parts.add(rowContent);
                        }
                    }
                    continue;
                }
                JsonNode blockDetails = subImage.path("BlockInfo").path("BlockDetails");
                if (blockDetails.isArray()) {
                    for (JsonNode block : blockDetails) {
                        String blockContent = readText(block, "BlockContent");
                        if (StringUtils.hasText(blockContent)) {
                            parts.add(blockContent);
                        }
                    }
                }
                continue;
            }
            parts.add(subContent);
        }
        return String.join("\n", parts);
    }

    private String readText(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode field = node.path(fieldName);
            if (field.isTextual()) {
                String text = normalizeText(field.asText(""));
                if (StringUtils.hasText(text)) {
                    return text;
                }
            }
        }
        return "";
    }

    private List<String> buildOrderedLines(OcrPayload payload) {
        if (payload.words.isEmpty()) {
            return splitRawLines(payload.content);
        }
        List<OcrWord> words = new ArrayList<OcrWord>(payload.words);
        Collections.sort(words, Comparator.comparingInt((OcrWord item) -> item.y).thenComparingInt(item -> item.x));
        List<WordLine> grouped = new ArrayList<WordLine>();
        for (OcrWord word : words) {
            if (!StringUtils.hasText(word.word)) {
                continue;
            }
            WordLine target = null;
            for (WordLine line : grouped) {
                if (Math.abs(line.centerY - word.centerY()) <= Math.max(12, Math.min(line.height, word.height))) {
                    target = line;
                    break;
                }
            }
            if (target == null) {
                target = new WordLine(word.centerY(), word.height);
                grouped.add(target);
            }
            target.add(word);
        }
        List<String> lines = new ArrayList<String>();
        for (WordLine line : grouped) {
            lines.add(line.toText());
        }
        return lines;
    }

    private List<String> splitRawLines(String rawText) {
        List<String> lines = new ArrayList<String>();
        if (!StringUtils.hasText(rawText)) {
            return lines;
        }
        for (String part : rawText.split("\\r?\\n")) {
            String normalized = normalizeText(part);
            if (StringUtils.hasText(normalized)) {
                lines.add(normalized);
            }
        }
        return lines;
    }

    private List<ReceiptRecognizedItemResponse> parseReceiptItemsFromRawText(String rawText) {
        return parseReceiptItems(splitRawLines(rawText));
    }

    private List<ReceiptRecognizedItemResponse> parseReceiptItems(List<String> lines) {
        Map<String, ReceiptRecognizedItemResponse> itemMap = new LinkedHashMap<String, ReceiptRecognizedItemResponse>();
        for (String line : lines) {
            ReceiptRecognizedItemResponse parsed = parseReceiptLine(line);
            if (parsed == null) {
                continue;
            }
            itemMap.put(parsed.getName(), parsed);
        }
        return new ArrayList<ReceiptRecognizedItemResponse>(itemMap.values());
    }

    private ReceiptRecognizedItemResponse parseReceiptLine(String line) {
        String normalized = normalizeReceiptLine(line);
        if (!StringUtils.hasText(normalized) || shouldIgnoreReceiptLine(normalized)) {
            return null;
        }
        normalized = normalized.replaceAll("\\s+\\d+(?:\\.\\d{1,2})$", "").trim();
        String unit = "";
        String quantity = "1";
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(.+?)\\s*(\\d+(?:\\.\\d+)?)\\s*(kg|KG|千克|公斤|g|G|克|斤|盒|包|袋|瓶|听|个|支|桶|片|条|罐|只|颗|枚|根|把|块|朵|箱|提)?$").matcher(normalized);
        String name = normalized;
        if (matcher.find()) {
            name = normalizeText(matcher.group(1));
            quantity = matcher.group(2);
            unit = normalizeUnit(matcher.group(3));
        } else {
            matcher = java.util.regex.Pattern.compile("(.+?)(kg|KG|千克|公斤|g|G|克|斤|盒|包|袋|瓶|听|个|支|桶|片|条|罐|只|颗|枚|根|把|块|朵|箱|提)$").matcher(normalized);
            if (matcher.find()) {
                name = normalizeText(matcher.group(1));
                unit = normalizeUnit(matcher.group(2));
            }
        }
        name = normalizeItemName(name);
        if (!StringUtils.hasText(name) || name.length() > 20) {
            return null;
        }
        return ReceiptRecognizedItemResponse.builder()
            .name(name)
            .quantity(quantity)
            .unit(StringUtils.hasText(unit) ? unit : "份")
            .build();
    }

    private boolean shouldIgnoreReceiptLine(String line) {
        String lower = line.toLowerCase();
        return lower.contains("合计")
            || lower.contains("实收")
            || lower.contains("优惠")
            || lower.contains("找零")
            || lower.contains("订单")
            || lower.contains("支付")
            || lower.contains("微信")
            || lower.contains("支付宝")
            || lower.contains("欢迎")
            || lower.contains("谢谢")
            || lower.contains("tel")
            || lower.contains("电话")
            || lower.matches(".*\\d{4}[-/.年]\\d{1,2}[-/.月]\\d{1,2}.*")
            || lower.matches(".*\\d{2}:\\d{2}(:\\d{2})?.*");
    }

    private String normalizeReceiptLine(String line) {
        String normalized = normalizeText(line);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        normalized = normalized
            .replace('×', ' ')
            .replace('*', ' ')
            .replaceAll("[¥￥]\\s*\\d+(?:\\.\\d{1,2})?", "")
            .replaceAll("\\s{2,}", " ")
            .trim();
        return normalized;
    }

    private String normalizeItemName(String name) {
        String normalized = normalizeText(name);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        normalized = normalized.replaceAll("^[0-9.\\-]+", "").trim();
        normalized = normalized.replaceAll("[()（）\\[\\]]", "");
        normalized = normalized.replaceAll("\\s{2,}", " ");
        return normalized;
    }

    private String parseRecipeName(List<String> lines) {
        for (String line : lines) {
            String normalized = normalizeText(line);
            if (!StringUtils.hasText(normalized)) {
                continue;
            }
            if (normalized.length() <= 18
                && !normalized.contains("食材")
                && !normalized.contains("用料")
                && !normalized.contains("步骤")
                && !normalized.contains("做法")) {
                return normalized;
            }
        }
        return "图片识别导入菜谱";
    }

    private List<String> parseRecipeIngredientNames(List<String> lines) {
        LinkedHashSet<String> names = new LinkedHashSet<String>();
        boolean ingredientSection = false;
        for (String line : lines) {
            String normalized = normalizeText(line);
            if (!StringUtils.hasText(normalized)) {
                continue;
            }
            if (normalized.contains("用料") || normalized.contains("食材") || normalized.contains("配料")) {
                ingredientSection = true;
                continue;
            }
            if (normalized.contains("步骤") || normalized.contains("做法") || normalized.contains("制作")) {
                ingredientSection = false;
            }
            if (ingredientSection || normalized.matches(".*\\d+(?:\\.\\d+)?\\s*(kg|KG|千克|公斤|g|G|克|斤|盒|包|袋|瓶|听|个|支|桶|片|条|罐|只|颗|枚|根|把|块|朵).*")) {
                String candidate = normalized.replaceAll("\\d+(?:\\.\\d+)?\\s*(kg|KG|千克|公斤|g|G|克|斤|盒|包|袋|瓶|听|个|支|桶|片|条|罐|只|颗|枚|根|把|块|朵).*", "").trim();
                candidate = normalizeItemName(candidate);
                if (StringUtils.hasText(candidate) && candidate.length() <= 20) {
                    names.add(candidate);
                }
            }
        }
        return new ArrayList<String>(names);
    }

    private String parseRecipeInstructions(List<String> lines, String rawText) {
        StringBuilder builder = new StringBuilder();
        boolean stepSection = false;
        for (String line : lines) {
            String normalized = normalizeText(line);
            if (!StringUtils.hasText(normalized)) {
                continue;
            }
            if (normalized.contains("步骤") || normalized.contains("做法") || normalized.contains("制作")) {
                stepSection = true;
                continue;
            }
            if (stepSection) {
                if (builder.length() > 0) {
                    builder.append('\n');
                }
                builder.append(normalized);
            }
        }
        if (builder.length() > 0) {
            return builder.toString();
        }
        return StringUtils.hasText(rawText) ? rawText.trim() : "";
    }

    private void validateImage(String fileName, byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) {
            throw new BizException("请先选择图片");
        }
        if (imageBytes.length > 5 * 1024 * 1024) {
            throw new BizException("图片不能超过 5MB");
        }
        String normalized = normalizeText(fileName);
        if (!StringUtils.hasText(normalized)) {
            return;
        }
        String lower = normalized.toLowerCase();
        if (!(lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp") || lower.endsWith(".bmp"))) {
            throw new BizException("仅支持 jpg、png、webp、bmp 图片");
        }
    }

    private AppProperties.Ocr requireOcrConfig() {
        AppProperties.Ocr ocr = appProperties.getOcr();
        if (ocr == null || !Boolean.TRUE.equals(ocr.getEnabled())) {
            throw new BizException("OCR 功能未开启");
        }
        if (!StringUtils.hasText(ocr.getEndpoint())
            || !StringUtils.hasText(ocr.getAccessKeyId())
            || !StringUtils.hasText(ocr.getAccessKeySecret())) {
            throw new BizException("OCR 配置不完整");
        }
        return ocr;
    }

    private String buildEndpoint(String endpoint) {
        String normalized = normalizeText(endpoint);
        if (!StringUtils.hasText(normalized)) {
            throw new BizException("OCR Endpoint 未配置");
        }
        if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
            return normalized;
        }
        return "https://" + normalized;
    }

    private Client createClient(AppProperties.Ocr ocr) {
        try {
            com.aliyun.teaopenapi.models.Config config = new com.aliyun.teaopenapi.models.Config()
                .setAccessKeyId(ocr.getAccessKeyId())
                .setAccessKeySecret(ocr.getAccessKeySecret());
            config.endpoint = buildEndpoint(ocr.getEndpoint()).replace("https://", "").replace("http://", "");
            return new Client(config);
        } catch (Exception ex) {
            throw new BizException("初始化 OCR 客户端失败");
        }
    }

    private String normalizeUnit(String unit) {
        if (!StringUtils.hasText(unit)) {
            return "";
        }
        if ("KG".equalsIgnoreCase(unit) || "公斤".equals(unit)) {
            return "kg";
        }
        if ("G".equalsIgnoreCase(unit)) {
            return "克";
        }
        return unit;
    }

    private String normalizeText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private static class OcrPayload {
        private final String content;
        private final List<OcrWord> words;

        private OcrPayload(String content, List<OcrWord> words) {
            this.content = content;
            this.words = words;
        }
    }

    private static class OcrWord {
        private final String word;
        private final int x;
        private final int y;
        private final int width;
        private final int height;

        private OcrWord(String word, int x, int y, int width, int height) {
            this.word = word;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height <= 0 ? 20 : height;
        }

        private int centerY() {
            return y + (height / 2);
        }
    }

    private static class WordLine {
        private final List<OcrWord> words = new ArrayList<OcrWord>();
        private int centerY;
        private int height;

        private WordLine(int centerY, int height) {
            this.centerY = centerY;
            this.height = height;
        }

        private void add(OcrWord word) {
            words.add(word);
            centerY = (centerY * (words.size() - 1) + word.centerY()) / words.size();
            height = Math.max(height, word.height);
        }

        private String toText() {
            Collections.sort(words, Comparator.comparingInt(item -> item.x));
            StringBuilder builder = new StringBuilder();
            for (OcrWord word : words) {
                if (builder.length() > 0) {
                    builder.append(' ');
                }
                builder.append(word.word.trim());
            }
            return builder.toString().replaceAll("\\s{2,}", " ").trim();
        }
    }
}
