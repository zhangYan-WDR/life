/* eslint-disable no-undef */
import request from "../../utils/request";
import localStorage from "../../utils/localStorage";

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

  showPlannedFeature(e) {
    const type = e.currentTarget.dataset.type;
    const targetUrl = type === "receipt" ? "/pages/fridge/edit" : "/pages/recipe-edit/index";
    wx.showModal({
      title: "功能正在规划中",
      content: "功能正在规划中，先用手动添加吧",
      confirmText: "去手动添加",
      success: (res) => {
        if (!res.confirm) {
          return;
        }
        wx.navigateTo({
          url: targetUrl,
        });
      },
    });
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
