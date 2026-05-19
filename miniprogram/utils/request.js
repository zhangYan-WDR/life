/* eslint-disable no-undef */
import localStorage from "./localStorage";

function getBaseUrl() {
  const app = getApp();
  return app.globalData.baseUrl || "http://127.0.0.1:8080/api";
}

function getToken() {
  return localStorage.getItem("life_token");
}

function request(options) {
  return new Promise((resolve, reject) => {
    wx.request({
      url: `${getBaseUrl()}${options.url}`,
      method: options.method || "GET",
      data: options.data || {},
      header: {
        "content-type": "application/json",
        ...(options.auth === false
          ? {}
          : {
              Authorization: `Bearer ${getToken() || ""}`,
            }),
      },
      success(res) {
        const payload = res.data || {};
        if (payload.success) {
          resolve(payload.data);
          return;
        }
        reject(new Error(payload.message || "请求失败"));
      },
      fail(err) {
        reject(err);
      },
    });
  });
}

export default request;
