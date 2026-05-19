/* eslint-disable no-undef */
import request from "../../utils/request";
import localStorage from "../../utils/localStorage";

Page({
  data: {
    familyName: "",
    inviteCode: "",
    creating: false,
    joining: false,
  },

  onFamilyNameInput(e) {
    this.setData({
      familyName: e.detail.value,
    });
  },

  onInviteCodeInput(e) {
    this.setData({
      inviteCode: e.detail.value.toUpperCase(),
    });
  },

  async createFamily() {
    if (!this.data.familyName.trim()) {
      wx.showToast({ title: "请输入家庭名称", icon: "none" });
      return;
    }
    this.setData({ creating: true });
    try {
      const family = await request({
        url: "/families",
        method: "POST",
        data: {
          familyName: this.data.familyName.trim(),
        },
      });
      localStorage.setItem("life_current_family", family);
      wx.reLaunch({
        url: "/pages/home/index",
      });
    } catch (error) {
      wx.showToast({ title: error.message, icon: "none" });
    } finally {
      this.setData({ creating: false });
    }
  },

  async joinFamily() {
    if (!this.data.inviteCode.trim()) {
      wx.showToast({ title: "请输入邀请码", icon: "none" });
      return;
    }
    this.setData({ joining: true });
    try {
      const family = await request({
        url: "/families/join",
        method: "POST",
        data: {
          inviteCode: this.data.inviteCode.trim(),
        },
      });
      localStorage.setItem("life_current_family", family);
      wx.reLaunch({
        url: "/pages/home/index",
      });
    } catch (error) {
      wx.showToast({ title: error.message, icon: "none" });
    } finally {
      this.setData({ joining: false });
    }
  },
});
