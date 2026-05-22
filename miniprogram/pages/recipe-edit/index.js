/* eslint-disable no-undef */
import request from "../../utils/request";
import localStorage from "../../utils/localStorage";

const MAX_COVER_SIZE = 2 * 1024 * 1024;
const ALLOWED_COVER_EXTENSIONS = ["jpg", "jpeg", "png", "webp", "gif"];
const REFERENCE_URL_REGEXP = /(https?:\/\/[^\s]+)|(www\.[^\s]+)/i;
const OCR_DRAFT_RECIPE_KEY = "life_recipe_import_draft";

function flattenCatalog(data) {
  return [
    ...(data.systemIngredients || []),
    ...(data.familyIngredients || []),
  ];
}

function toSelectedItem(item, fallbackUnit) {
  if (!item) {
    return null;
  }
  return {
    id: item.id,
    sourceType: item.sourceType,
    name: item.name,
    category: item.category,
    secondaryCategory: item.secondaryCategory || "",
    defaultUnit: item.defaultUnit || fallbackUnit || "",
  };
}

function createIngredientLine(catalog) {
  const first = catalog[0] || null;
  return {
    sourceType: first ? first.sourceType : "SYSTEM",
    sourceId: first ? first.id : null,
    selectedItem: toSelectedItem(first),
    quantity: "1",
    unit: first ? (first.defaultUnit || "") : "",
  };
}

Page({
  data: {
    id: null,
    catalog: [],
    saving: false,
    coverPreviewUrl: "",
    coverLocalPath: "",
    recognizedIngredientNames: [],
    recognitionRawText: "",
    form: {
      name: "",
      baseServings: "2",
      instructions: "",
      note: "",
      coverUrl: "",
      coverObjectKey: "",
      referenceUrl: "",
      ingredients: [],
    },
  },

  async onLoad(query) {
    this.setData({
      id: query.id || null,
    });
    await this.loadCatalog();
    if (query.id) {
      await this.loadRecipe(query.id);
    } else {
      this.setData({
        "form.ingredients": [createIngredientLine(this.data.catalog)],
      });
      await this.applyRecognitionDraft();
    }
  },

  async applyRecognitionDraft() {
    const draft = localStorage.getItem(OCR_DRAFT_RECIPE_KEY);
    if (!draft) {
      return;
    }
    localStorage.removeItem(OCR_DRAFT_RECIPE_KEY);
    const recognizedIngredients = Array.isArray(draft.ingredients) && draft.ingredients.length
      ? draft.ingredients
      : (draft.ingredientNames || []).map((name) => ({ name, quantity: "1", unit: "" }));
    const recognizedIngredientNames = recognizedIngredients.map((it) => it.name).filter(Boolean);
    this.setData({
      "form.name": draft.name || "",
      "form.instructions": draft.instructions || "",
      "form.note": draft.note || "",
      recognizedIngredientNames,
      recognitionRawText: draft.rawText || "",
    });
    if (recognizedIngredients.length) {
      await this.applyRecognizedIngredients(recognizedIngredients);
    }
  },

  normalizeIngredientName(name) {
    return (name || "").trim().replace(/\s+/g, "");
  },

  findCatalogItemByName(name) {
    const normalized = this.normalizeIngredientName(name);
    return (this.data.catalog || []).find((item) => this.normalizeIngredientName(item.name) === normalized) || null;
  },

  async findRemoteCatalogItemByName(name) {
    const keyword = (name || "").trim();
    if (!keyword) {
      return null;
    }
    const response = await request({
      url: "/ingredients/search",
      data: {
        keyword,
        limit: 20,
      },
    });
    const normalized = this.normalizeIngredientName(keyword);
    return (response.items || []).find((item) => this.normalizeIngredientName(item.name) === normalized) || null;
  },

  async applyRecognizedIngredients(items) {
    const seen = new Set();
    const normalized = [];
    (items || []).forEach((raw) => {
      const name = this.normalizeIngredientName(raw && raw.name);
      if (!name || seen.has(name)) return;
      seen.add(name);
      normalized.push({
        name,
        quantity: raw && raw.quantity ? `${raw.quantity}` : "1",
        unit: (raw && raw.unit) || "",
      });
    });
    if (!normalized.length) {
      return;
    }
    const ingredientLines = [];
    for (const item of normalized) {
      let catalogItem = this.findCatalogItemByName(item.name);
      if (!catalogItem) {
        catalogItem = await this.findRemoteCatalogItemByName(item.name);
      }
      if (!catalogItem) {
        continue;
      }
      ingredientLines.push({
        sourceType: catalogItem.sourceType,
        sourceId: catalogItem.id,
        selectedItem: toSelectedItem(catalogItem),
        quantity: item.quantity || "1",
        unit: item.unit || catalogItem.defaultUnit || "份",
      });
    }
    if (ingredientLines.length) {
      this.setData({
        "form.ingredients": ingredientLines,
      });
    }
  },

  async loadCatalog() {
    const data = await request({
      url: "/ingredients/catalog",
      data: {
        includeSystem: false,
      },
    });
    this.setData({
      catalog: flattenCatalog(data),
    });
  },

  async loadRecipe(id) {
    try {
      const recipe = await request({ url: `/recipes/${id}` });
      const ingredients = (recipe.ingredients || []).map((item) => ({
        sourceType: item.sourceType,
        sourceId: item.sourceId,
        selectedItem: toSelectedItem({
          id: item.sourceId,
          sourceType: item.sourceType,
          name: item.name,
          category: item.category,
          defaultUnit: item.unit,
        }, item.unit),
        quantity: `${item.quantity}`,
        unit: item.unit,
      }));
      this.setData({
        form: {
          name: recipe.name || "",
          baseServings: `${recipe.baseServings || 2}`,
          instructions: recipe.instructions || "",
          note: recipe.note || "",
          coverUrl: recipe.coverUrl || "",
          coverObjectKey: recipe.coverObjectKey || "",
          referenceUrl: recipe.referenceUrl || "",
          ingredients: ingredients.length ? ingredients : [createIngredientLine(this.data.catalog)],
        },
        coverPreviewUrl: recipe.coverUrl || "",
        coverLocalPath: "",
      });
    } catch (error) {
      wx.showToast({ title: error.message, icon: "none" });
    }
  },

  onFieldInput(e) {
    const field = e.currentTarget.dataset.field;
    this.setData({
      [`form.${field}`]: e.detail.value,
    });
  },

  onIngredientFieldInput(e) {
    const index = Number(e.currentTarget.dataset.index);
    const field = e.currentTarget.dataset.field;
    this.setData({
      [`form.ingredients[${index}].${field}`]: e.detail.value,
    });
  },

  onIngredientSourceChange(e) {
    const index = Number(e.currentTarget.dataset.index);
    const selected = e.detail.item;
    if (!selected) {
      return;
    }
    this.setData({
      [`form.ingredients[${index}].sourceType`]: selected.sourceType,
      [`form.ingredients[${index}].sourceId`]: selected.id,
      [`form.ingredients[${index}].selectedItem`]: toSelectedItem(selected),
      [`form.ingredients[${index}].unit`]: selected.defaultUnit,
    });
  },

  addIngredient() {
    const next = (this.data.form.ingredients || []).concat([createIngredientLine(this.data.catalog)]);
    this.setData({
      "form.ingredients": next,
    });
  },

  removeIngredient(e) {
    const index = Number(e.currentTarget.dataset.index);
    const ingredients = (this.data.form.ingredients || []).slice();
    ingredients.splice(index, 1);
    this.setData({
      "form.ingredients": ingredients.length ? ingredients : [createIngredientLine(this.data.catalog)],
    });
  },

  goIngredients() {
    wx.navigateTo({
      url: "/pages/ingredients/index",
    });
  },

  async chooseCover() {
    try {
      const res = await new Promise((resolve, reject) => {
        wx.chooseImage({
          count: 1,
          sizeType: ["compressed"],
          sourceType: ["album", "camera"],
          success: resolve,
          fail: reject,
        });
      });
      const file = (res.tempFiles || [])[0] || {};
      const filePath = file.path || (res.tempFilePaths || [])[0];
      if (!filePath) {
        return;
      }
      if ((file.size || 0) > MAX_COVER_SIZE) {
        wx.showToast({ title: "封面图片不能超过 2MB", icon: "none" });
        return;
      }
      const extension = this.resolveExtension(filePath);
      if (!ALLOWED_COVER_EXTENSIONS.includes(extension)) {
        wx.showToast({ title: "仅支持 jpg、png、webp、gif", icon: "none" });
        return;
      }
      this.setData({
        coverPreviewUrl: filePath,
        coverLocalPath: filePath,
      });
    } catch (error) {
      if (error && error.errMsg && error.errMsg.indexOf("cancel") >= 0) {
        return;
      }
      wx.showToast({ title: "选择封面失败", icon: "none" });
    }
  },

  removeCover() {
    this.setData({
      coverPreviewUrl: "",
      coverLocalPath: "",
      "form.coverUrl": "",
      "form.coverObjectKey": "",
    });
  },

  resolveExtension(filePath) {
    const index = (filePath || "").lastIndexOf(".");
    return index >= 0 ? filePath.substring(index + 1).toLowerCase() : "";
  },

  extractReferenceUrl(value) {
    const text = (value || "").trim();
    if (!text) {
      return "";
    }
    const matched = text.match(REFERENCE_URL_REGEXP);
    if (!matched || !matched[0]) {
      return "";
    }
    let url = matched[0].replace(/[.,;:!?)]}>\u3002\uff0c\uff1b\uff1a\uff01\uff1f\u3001]+$/g, "");
    if (/^www\./i.test(url)) {
      url = `https://${url}`;
    }
    return url;
  },

  buildPayload() {
    const referenceUrl = this.extractReferenceUrl(this.data.form.referenceUrl);
    return {
      name: this.data.form.name,
      baseServings: Number(this.data.form.baseServings),
      instructions: this.data.form.instructions,
      note: this.data.form.note,
      coverUrl: this.data.form.coverUrl,
      coverObjectKey: this.data.form.coverObjectKey,
      referenceUrl,
      ingredients: (this.data.form.ingredients || []).map((item) => ({
        sourceType: item.sourceType,
        sourceId: item.sourceId,
        quantity: Number(item.quantity),
        unit: item.unit,
      })),
    };
  },

  async requestCoverUploadPolicy(recipeId, filePath) {
    const fileName = (filePath || "").split("/").pop() || "cover.jpg";
    return request({
      url: `/recipes/${recipeId}/cover-upload-policy`,
      method: "POST",
      data: {
        originalFileName: fileName,
      },
    });
  },

  async uploadCover(recipeId, filePath) {
    const policy = await this.requestCoverUploadPolicy(recipeId, filePath);
    await new Promise((resolve, reject) => {
      wx.uploadFile({
        url: policy.uploadHost,
        filePath,
        name: "file",
        formData: {
          key: policy.objectKey,
          policy: policy.policy,
          OSSAccessKeyId: policy.accessKeyId,
          signature: policy.signature,
          success_action_status: policy.successActionStatus,
        },
        success: (res) => {
          if (res.statusCode >= 200 && res.statusCode < 300) {
            resolve();
            return;
          }
          reject(new Error("上传封面失败"));
        },
        fail: reject,
      });
    });
    return {
      coverUrl: policy.publicUrl,
      coverObjectKey: policy.objectKey,
    };
  },

  async saveRecipe() {
    if (this.data.saving) {
      return;
    }
    if ((this.data.form.ingredients || []).some((item) => !item.sourceId)) {
      wx.showToast({ title: "还有食材未选择", icon: "none" });
      return;
    }
    if (this.data.form.referenceUrl && !this.extractReferenceUrl(this.data.form.referenceUrl)) {
      wx.showToast({ title: "参考链接里没找到可用地址", icon: "none" });
      return;
    }
    const finalPayload = this.buildPayload();
    const hasPendingCoverUpload = !!this.data.coverLocalPath;
    this.setData({ saving: true });
    wx.showLoading({ title: "保存中...", mask: true });
    try {
      let recipeId = this.data.id;
      if (this.data.id) {
        await request({
          url: `/recipes/${this.data.id}`,
          method: "PUT",
          data: hasPendingCoverUpload
            ? {
                ...finalPayload,
                coverUrl: this.data.form.coverUrl,
                coverObjectKey: this.data.form.coverObjectKey,
              }
            : finalPayload,
        });
      } else {
        const created = await request({
          url: "/recipes",
          method: "POST",
          data: hasPendingCoverUpload
            ? {
                ...finalPayload,
                coverUrl: "",
                coverObjectKey: "",
              }
            : finalPayload,
        });
        recipeId = created.id;
        this.setData({
          id: recipeId,
        });
      }

      if (hasPendingCoverUpload) {
        wx.showLoading({ title: "上传封面...", mask: true });
        const cover = await this.uploadCover(recipeId, this.data.coverLocalPath);
        await request({
          url: `/recipes/${recipeId}`,
          method: "PUT",
          data: {
            ...finalPayload,
            coverUrl: cover.coverUrl,
            coverObjectKey: cover.coverObjectKey,
          },
        });
      }
      wx.hideLoading();
      wx.navigateBack();
    } catch (error) {
      wx.hideLoading();
      wx.showToast({ title: error.message, icon: "none" });
    } finally {
      this.setData({ saving: false });
    }
  },
});
