/* eslint-disable no-undef */
import request from "../../utils/request";

const TABS = [
  { key: "PENDING", label: "待我处理" },
  { key: "MINE", label: "我发起的" },
  { key: "ALL", label: "全部历史" },
];

const STATUS_LABEL = {
  PENDING: "待表态",
  APPROVED: "已确认",
  REJECTED: "已取消",
};

Page({
  data: {
    activeTab: "PENDING",
    tabs: TABS,
    items: [],
    loading: true,
  },

  onShow() {
    this.loadItems();
  },

  async loadItems() {
    this.setData({ loading: true });
    try {
      const items = await request({
        url: `/meal-requests?view=${this.data.activeTab}`,
      });
      this.setData({
        items: (items || []).map((item) => ({
          ...item,
          recipeNamesText: (item.recipeNames || []).join("、"),
          statusCls: (item.status || "").toLowerCase(),
          statusLabel: STATUS_LABEL[item.status] || item.status,
        })),
      });
    } catch (error) {
      wx.showToast({ title: error.message, icon: "none" });
    } finally {
      this.setData({ loading: false });
    }
  },

  changeTab(e) {
    const activeTab = e.currentTarget.dataset.key;
    if (activeTab === this.data.activeTab) {
      return;
    }
    this.setData({ activeTab });
    this.loadItems();
  },

  createMealRequest() {
    wx.navigateTo({
      url: "/pages/meal-request-edit/index",
    });
  },

  openMealRequest(e) {
    wx.navigateTo({
      url: `/pages/meal-request-detail/index?id=${e.currentTarget.dataset.id}`,
    });
  },
});
