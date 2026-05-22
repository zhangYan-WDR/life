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
import com.life.server.dto.response.RecognizedIngredientResponse;
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
        log.info("receipt OCR: lineCount={}, contentLen={}", lines.size(), payload.content == null ? 0 : payload.content.length());
        if (lines.isEmpty() && !StringUtils.hasText(payload.content)) {
            throw new BizException("OCR 没有识别到任何文字，请检查图片是否清晰");
        }
        String flatText = lines.isEmpty() ? payload.content : String.join(" ", lines);
        log.info("receipt OCR raw lines:\n{}", String.join("\n", lines));
        List<ReceiptRecognizedItemResponse> items = parseReceiptItems(lines);
        if (items.isEmpty()) {
            items = parseReceiptItemsFromRawText(payload.content);
        }
        if (items.isEmpty()) {
            items = extractByBarcode(flatText);
        }
        if (items.isEmpty()) {
            items = extractByQuantityMarker(flatText);
        }
        log.info("receipt parsed items: {}", items.size());
        if (items.isEmpty()) {
            throw new BizException("OCR 识别到了 " + lines.size() + " 行文字，但没有匹配到商品条目，请换一张更清晰的小票试试");
        }
        return ReceiptImageRecognitionResponse.builder()
            .rawText(payload.content)
            .lines(lines)
            .items(items)
            .build();
    }

    private static final java.util.regex.Pattern BARCODE_PATTERN = java.util.regex.Pattern.compile("\\d{10,}");
    private static final java.util.regex.Pattern NUMBER_PATTERN = java.util.regex.Pattern.compile("\\d+(?:\\.\\d+)?");
    private static final java.util.Set<String> RECEIPT_HEADER_KEYWORDS = new java.util.HashSet<String>(java.util.Arrays.asList(
        "商品名称", "单价", "数量", "成交金额", "金额", "订单号", "交易时间", "收银员", "机台号",
        "欢迎您", "意见反馈", "发票开具", "总积分", "应收", "实收", "找零", "支付宝", "微信",
        "件数", "手机会员账号"
    ));

    private List<ReceiptRecognizedItemResponse> extractByBarcode(String flatText) {
        List<ReceiptRecognizedItemResponse> result = new ArrayList<ReceiptRecognizedItemResponse>();
        if (!StringUtils.hasText(flatText)) return result;
        java.util.regex.Matcher m = BARCODE_PATTERN.matcher(flatText);
        List<int[]> positions = new ArrayList<int[]>();
        while (m.find()) {
            // Skip very long digit runs that look like timestamps / order ids embedded in known sections
            positions.add(new int[]{m.start(), m.end()});
        }
        if (positions.isEmpty()) return result;
        java.util.Set<String> seen = new java.util.LinkedHashSet<String>();
        for (int i = 0; i < positions.size(); i++) {
            int[] pos = positions.get(i);
            int prevEnd = i == 0 ? 0 : positions.get(i - 1)[1];
            int nextStart = i + 1 < positions.size() ? positions.get(i + 1)[0] : flatText.length();
            String before = flatText.substring(prevEnd, pos[0]).trim();
            String after = flatText.substring(pos[1], nextStart).trim();
            String name = extractTrailingProductName(before);
            if (!StringUtils.hasText(name)) continue;
            String quantity = inferQuantityFromNumbers(after);
            if (seen.contains(name)) continue;
            seen.add(name);
            result.add(ReceiptRecognizedItemResponse.builder()
                .name(name)
                .quantity(quantity)
                .unit(guessUnitFromName(name))
                .build());
        }
        return result;
    }

    private String extractTrailingProductName(String chunk) {
        if (!StringUtils.hasText(chunk)) return null;
        String[] tokens = chunk.split("\\s+");
        List<String> collected = new ArrayList<String>();
        for (int i = tokens.length - 1; i >= 0; i--) {
            String t = tokens[i].trim();
            if (t.isEmpty()) continue;
            if (NUMBER_PATTERN.matcher(t).matches()) break;
            if (RECEIPT_HEADER_KEYWORDS.contains(t)) break;
            collected.add(0, t);
        }
        if (collected.isEmpty()) return null;
        String name = String.join(" ", collected);
        name = name.replaceAll("[（(][^）)]*[）)]", "").trim();
        name = name.replaceAll("\\s{2,}", " ");
        if (name.length() < 1 || name.length() > 30) return null;
        // require at least one chinese char OR letter
        if (!name.matches(".*[\\u4e00-\\u9fa5A-Za-z].*")) return null;
        return name;
    }

    private String inferQuantityFromNumbers(String chunk) {
        if (!StringUtils.hasText(chunk)) return "1";
        // Stop scanning at first chinese-text run that looks like a section header
        String safe = chunk;
        java.util.regex.Matcher stop = java.util.regex.Pattern.compile("[\\u4e00-\\u9fa5]{3,}").matcher(chunk);
        if (stop.find() && stop.start() > 0) {
            safe = chunk.substring(0, stop.start());
        }
        java.util.regex.Matcher numMatcher = NUMBER_PATTERN.matcher(safe);
        List<String> nums = new ArrayList<String>();
        while (numMatcher.find()) {
            nums.add(numMatcher.group());
            if (nums.size() >= 4) break;
        }
        if (nums.size() >= 3) return nums.get(nums.size() - 2);
        if (nums.size() == 2) {
            if (nums.get(0).equals(nums.get(1))) return "1";
            return nums.get(1);
        }
        return "1";
    }

    private List<ReceiptRecognizedItemResponse> extractByQuantityMarker(String flatText) {
        List<ReceiptRecognizedItemResponse> result = new ArrayList<ReceiptRecognizedItemResponse>();
        if (!StringUtils.hasText(flatText)) return result;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
            "数量[：:]\\s*(\\d+(?:\\.\\d+)?)\\s*([\\u4e00-\\u9fa5A-Za-z]{0,3})"
        ).matcher(flatText);
        int lastEnd = 0;
        java.util.Set<String> seen = new java.util.LinkedHashSet<String>();
        while (m.find()) {
            String chunk = flatText.substring(lastEnd, m.start()).trim();
            String name = extractItemNameFromOrderChunk(chunk);
            String quantity = m.group(1);
            String unit = m.group(2);
            lastEnd = m.end();
            if (!StringUtils.hasText(name) || seen.contains(name)) continue;
            seen.add(name);
            result.add(ReceiptRecognizedItemResponse.builder()
                .name(name)
                .quantity(quantity)
                .unit(StringUtils.hasText(unit) ? unit : guessUnitFromName(name))
                .build());
        }
        return result;
    }

    private String extractItemNameFromOrderChunk(String chunk) {
        if (!StringUtils.hasText(chunk)) return null;
        // Remove price markers and ad text
        String cleaned = chunk
            .replaceAll("实付[¥￥]?\\s*\\d+(?:\\.\\d+)?", " ")
            .replaceAll("原价[：:]?\\s*[¥￥]?\\s*\\d+(?:\\.\\d+)?(?:/\\S+)?", " ")
            .replaceAll("[¥￥]\\s*\\d+(?:\\.\\d+)?", " ")
            .replaceAll("申请退款|加购物车|安心检测|安心淘", " ")
            .replaceAll("\\s{2,}", " ")
            .trim();
        // Take last whitespace-separated token that contains Chinese
        String[] parts = cleaned.split("\\s+");
        for (int i = parts.length - 1; i >= 0; i--) {
            String p = parts[i].trim();
            if (p.length() < 2 || p.length() > 30) continue;
            if (!p.matches(".*[\\u4e00-\\u9fa5].*")) continue;
            return p.replaceAll("[（(][^）)]*[）)]", "").trim();
        }
        return null;
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
        List<RecognizedIngredientResponse> ingredients = parseRecipeIngredients(lines);
        List<String> ingredientNames = new ArrayList<String>();
        for (RecognizedIngredientResponse ing : ingredients) {
            ingredientNames.add(ing.getName());
        }
        String instructions = parseRecipeInstructions(lines, payload.content);
        String note = ingredients.isEmpty() ? "图片识别已导入原文，请检查后再保存。" : "识别到的食材会展示在编辑页里，记得手动映射到食材目录。";
        return RecipeImageRecognitionResponse.builder()
            .name(name)
            .instructions(instructions)
            .note(note)
            .ingredientNames(ingredientNames)
            .ingredients(ingredients)
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
        // Pass 1: lines with inline quantity (e.g. "苹果 2 个 8.00")
        for (String line : lines) {
            ReceiptRecognizedItemResponse parsed = parseReceiptLine(line);
            if (parsed == null) {
                continue;
            }
            itemMap.put(parsed.getName(), parsed);
        }
        // Pass 2: columnar layout — name on one line, barcode on next, "单价 数量 金额" on the line after
        for (int i = 0; i < lines.size(); i++) {
            String line = normalizeText(lines.get(i));
            if (!StringUtils.hasText(line)) continue;
            ReceiptRecognizedItemResponse fromColumns = parseColumnarItem(lines, i);
            if (fromColumns != null && !itemMap.containsKey(fromColumns.getName())) {
                itemMap.put(fromColumns.getName(), fromColumns);
            }
        }
        return new ArrayList<ReceiptRecognizedItemResponse>(itemMap.values());
    }

    private ReceiptRecognizedItemResponse parseColumnarItem(List<String> lines, int idx) {
        String nameLine = normalizeText(lines.get(idx));
        if (!StringUtils.hasText(nameLine) || shouldIgnoreReceiptLine(nameLine)) return null;
        // Skip lines that look like numeric / barcode / header / total
        if (nameLine.matches("^[\\d\\s\\.\\-:×*]+$")) return null;
        if (nameLine.matches("^[\\d]{6,}.*")) return null; // starts with long digit run
        if (nameLine.length() > 30) return null;
        // Must contain Chinese characters to look like a product name
        if (!nameLine.matches(".*[\\u4e00-\\u9fa5].*")) return null;
        // Look ahead up to 3 lines for a "数字 数字 数字" pattern (单价 数量 金额)
        for (int j = idx + 1; j <= Math.min(idx + 3, lines.size() - 1); j++) {
            String probe = normalizeText(lines.get(j));
            if (!StringUtils.hasText(probe)) continue;
            java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "(\\d+(?:\\.\\d+)?)\\s+(\\d+(?:\\.\\d+)?)\\s+(\\d+(?:\\.\\d+)?)"
            ).matcher(probe);
            if (m.find()) {
                String quantity = m.group(2);
                String name = normalizeItemName(stripParenthesesContent(nameLine));
                if (!StringUtils.hasText(name) || name.length() > 20) return null;
                return ReceiptRecognizedItemResponse.builder()
                    .name(name)
                    .quantity(quantity)
                    .unit(guessUnitFromName(name))
                    .build();
            }
        }
        return null;
    }

    private String stripParenthesesContent(String name) {
        if (name == null) return null;
        return name.replaceAll("[（(][^）)]*[）)]", "").trim();
    }

    private String guessUnitFromName(String name) {
        if (name == null) return "份";
        if (name.matches(".*\\d+\\s*(kg|KG|千克|公斤)$")) return "kg";
        if (name.matches(".*\\d+\\s*(g|G|克)$")) return "克";
        if (name.matches(".*\\d+\\s*(ml|ML|毫升)$")) return "毫升";
        if (name.matches(".*\\d+\\s*L$") || name.endsWith("升")) return "升";
        return "份";
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

    private static final java.util.regex.Pattern RECIPE_INGREDIENT_PATTERN = java.util.regex.Pattern.compile(
        "(.+?)\\s*(\\d+(?:\\.\\d+)?)\\s*(kg|KG|千克|公斤|g|G|克|斤|盒|包|袋|瓶|听|个|支|桶|片|条|罐|只|颗|枚|根|把|块|朵|勺|匙|汤匙|茶匙|大勺|小勺|适量|少许|ml|ML|毫升|升|杯|碗)");

    private List<RecognizedIngredientResponse> parseRecipeIngredients(List<String> lines) {
        LinkedHashMap<String, RecognizedIngredientResponse> map = new LinkedHashMap<String, RecognizedIngredientResponse>();
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
            java.util.regex.Matcher matcher = RECIPE_INGREDIENT_PATTERN.matcher(normalized);
            if (ingredientSection || matcher.find()) {
                matcher.reset();
                if (matcher.find()) {
                    String name = normalizeItemName(matcher.group(1));
                    String quantity = matcher.group(2);
                    String unit = normalizeUnit(matcher.group(3));
                    if (StringUtils.hasText(name) && name.length() <= 20) {
                        if (!map.containsKey(name)) {
                            map.put(name, RecognizedIngredientResponse.builder()
                                .name(name)
                                .quantity(quantity)
                                .unit(StringUtils.hasText(unit) ? unit : "份")
                                .build());
                        }
                    }
                } else if (ingredientSection) {
                    String candidate = normalizeItemName(normalized);
                    if (StringUtils.hasText(candidate) && candidate.length() <= 20 && !map.containsKey(candidate)) {
                        map.put(candidate, RecognizedIngredientResponse.builder()
                            .name(candidate)
                            .quantity("1")
                            .unit("份")
                            .build());
                    }
                }
            }
        }
        return new ArrayList<RecognizedIngredientResponse>(map.values());
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
        if (imageBytes.length > 10 * 1024 * 1024) {
            throw new BizException("图片不能超过 10MB");
        }
        log.info("validateImage fileName={}, bytes={}", fileName, imageBytes.length);
        String normalized = normalizeText(fileName);
        if (!StringUtils.hasText(normalized)) {
            return;
        }
        String lower = normalized.toLowerCase();
        // Permissive: accept anything without an extension (wx temp files), reject only obvious non-images
        if (lower.contains(".")) {
            if (!(lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")
                || lower.endsWith(".webp") || lower.endsWith(".bmp") || lower.endsWith(".tmp"))) {
                throw new BizException("仅支持 jpg、png、webp、bmp 图片");
            }
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
