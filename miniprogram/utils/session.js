/* eslint-disable no-undef */
import localStorage from "./localStorage";
import request from "./request";

const TOKEN_KEY = "life_token";
const DEVICE_KEY = "life_debug_user";

function getDebugUserKey() {
  let key = localStorage.getItem(DEVICE_KEY);
  if (!key) {
    const info = wx.getSystemInfoSync();
    key = `${info.brand || "dev"}-${info.model || "simulator"}`;
    localStorage.setItem(DEVICE_KEY, key);
  }
  return key;
}

export async function ensureLogin() {
  const token = localStorage.getItem(TOKEN_KEY);
  if (token) {
    return token;
  }
  const loginResult = await new Promise((resolve, reject) => {
    wx.login({
      success: resolve,
      fail: reject,
    });
  });
  const data = await request({
    url: "/auth/wx-login",
    method: "POST",
    auth: false,
    data: {
      code: loginResult.code,
      nickname: "生活用户",
      debugUserKey: getDebugUserKey(),
    },
  });
  localStorage.setItem(TOKEN_KEY, data.token);
  getApp().globalData.token = data.token;
  return data.token;
}

export function clearSession() {
  localStorage.removeItem(TOKEN_KEY);
  getApp().globalData.token = "";
}
