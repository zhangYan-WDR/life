/* eslint-disable no-undef */
import request from "../../utils/request";
import localStorage from "../../utils/localStorage";

const OCR_DRAFT_RECEIPT_KEY = "life_receipt_import_draft";
const OCR_DRAFT_RECIPE_KEY = "life_recipe_import_draft";

Page({
  data: {
    family: null,
    summary: {
      total: 0,
      expiringSoon: 0,
      expired: 0,
    },
    pendingMealRequests: [],
    loading: true,
    errorText: "",
    memberCount: 0,
    welcomeTitle: "今晚家里一切安稳",
    displayMembers: [],
  },

  onLoad() {
    const cachedFamily = localStorage.getItem("life_current_family");
    if (cachedFamily) {
      this.setData({
        family: cachedFamily,
        memberCount: (cachedFamily.members || []).length,
        welcomeTitle: this.buildWelcomeTitle(cachedFamily),
        displayMembers: this.buildDisplayMembers(cachedFamily.members || []),
      });
    }
  },

  onShow() {
    this.loadPage();
  },

  async loadPage() {
    this.setData({
      loading: true,
      errorText: "",
    });
    try {
      const family = await request({ url: "/families/current" });
      this.setData({
        family,
        memberCount: (family.members || []).length,
        welcomeTitle: this.buildWelcomeTitle(family),
        displayMembers: this.buildDisplayMembers(family.members || []),
      });
      localStorage.setItem("life_current_family", family);
    } catch (error) {
      this.setData({
        errorText: error.message || "家庭信息加载失败",
      });
    }

    try {
      const summary = await request({ url: "/fridge/reminders/summary" });
      this.setData({
        summary,
      });
    } catch (error) {
      const fallbackMessage = error.message || "库存汇总加载失败";
      this.setData({
        errorText: this.data.errorText || fallbackMessage,
      });
    }

    try {
      const pendingMealRequests = await request({ url: "/meal-requests?view=PENDING" });
      this.setData({
        pendingMealRequests: (pendingMealRequests || []).map((item) => ({
          ...item,
          recipeNamesText: (item.recipeNames || []).join("、"),
        })),
      });
    } catch (error) {
      const fallbackMessage = error.message || "点餐待办加载失败";
      this.setData({
        errorText: this.data.errorText || fallbackMessage,
      });
    } finally {
      this.setData({
        loading: false,
      });
    }
  },

  buildWelcomeTitle(family) {
    const count = (family.members || []).length;
    if (count <= 1) {
      return "一个人的生活面板，也可以很有秩序";
    }
    return `${family.familyName} 现在有 ${count} 位成员在线协作`;
  },

  buildDisplayMembers(members) {
    return members.map((item) => ({
      ...item,
      initial: item.nickname ? item.nickname.slice(0, 1) : "家",
      roleLabel: item.role === "OWNER" ? "创建者" : "成员",
    }));
  },

  goFridge() {
    wx.navigateTo({
      url: "/pages/fridge/index",
    });
  },

  goFridgeExpiring() {
    wx.navigateTo({
      url: "/pages/fridge/index?tab=EXPIRING",
    });
  },

  goFridgeExpired() {
    wx.navigateTo({
      url: "/pages/fridge/index?tab=EXPIRED",
    });
  },

  goIngredients() {
    wx.navigateTo({
      url: "/pages/ingredients/index",
    });
  },

  goRecipes() {
    wx.navigateTo({
      url: "/pages/recipes/index",
    });
  },

  goMealRequests() {
    wx.navigateTo({
      url: "/pages/meal-requests/index",
    });
  },

  openMealRequest(e) {
    wx.navigateTo({
      url: `/pages/meal-request-detail/index?id=${e.currentTarget.dataset.id}`,
    });
  },

  chooseImage() {
    return new Promise((resolve, reject) => {
      wx.chooseImage({
        count: 1,
        sizeType: ["compressed"],
        sourceType: ["album", "camera"],
        success: resolve,
        fail: reject,
      });
    });
  },

  uploadRecognition(url, filePath) {
    return new Promise((resolve, reject) => {
      const token = localStorage.getItem("life_token");
      const app = getApp();
      const baseUrl = app.globalData.baseUrl || "http://127.0.0.1:8080/api";
      wx.uploadFile({
        url: `${baseUrl}${url}`,
        filePath,
        name: "file",
        header: {
          Authorization: `Bearer ${token || ""}`,
        },
        success: (res) => {
          try {
            const payload = JSON.parse(res.data || "{}");
            if (payload.success) {
              resolve(payload.data);
              return;
            }
            reject(new Error(payload.message || "识别失败"));
          } catch (error) {
            reject(new Error("识别结果解析失败"));
          }
        },
        fail: reject,
      });
    });
  },

  async recognizeReceipt() {
    try {
      const image = await this.chooseImage();
      const filePath = image.tempFilePaths && image.tempFilePaths[0];
      if (!filePath) {
        return;
      }
      wx.showLoading({ title: "正在识别小票...", mask: true });
      const result = await this.uploadRecognition("/image-recognition/receipt", filePath);
      wx.hideLoading();
      localStorage.setItem(OCR_DRAFT_RECEIPT_KEY, {
        ...result,
        imagePath: filePath,
      });
      wx.navigateTo({
        url: "/pages/receipt-import/index",
      });
    } catch (error) {
      wx.hideLoading();
      if (error && error.errMsg && error.errMsg.indexOf("cancel") >= 0) {
        return;
      }
      wx.showToast({ title: error.message || "识别失败", icon: "none" });
    }
  },

  async recognizeRecipe() {
    try {
      const image = await this.chooseImage();
      const filePath = image.tempFilePaths && image.tempFilePaths[0];
      if (!filePath) {
        return;
      }
      wx.showLoading({ title: "正在识别菜谱...", mask: true });
      const result = await this.uploadRecognition("/image-recognition/recipe", filePath);
      wx.hideLoading();
      localStorage.setItem(OCR_DRAFT_RECIPE_KEY, {
        ...result,
        imagePath: filePath,
      });
      wx.navigateTo({
        url: "/pages/recipe-edit/index?from=imageRecognition",
      });
    } catch (error) {
      wx.hideLoading();
      if (error && error.errMsg && error.errMsg.indexOf("cancel") >= 0) {
        return;
      }
      wx.showToast({ title: error.message || "识别失败", icon: "none" });
    }
  },

  subscribeReminder() {
    request({
      url: "/subscriptions/expiry-reminder",
      method: "POST",
      data: { accepted: true },
    }).then(() => {
      wx.showToast({ title: "已标记提醒订阅", icon: "success" });
    }).catch((error) => {
      wx.showToast({ title: error.message, icon: "none" });
    });
  },

  subscribeMealReminder() {
    request({
      url: "/subscriptions/meal-request-reminder",
      method: "POST",
      data: { accepted: true },
    }).then(() => {
      wx.showToast({ title: "已标记点餐提醒", icon: "success" });
    }).catch((error) => {
      wx.showToast({ title: error.message, icon: "none" });
    });
  },

  retryLoad() {
    this.loadPage();
  },
});
