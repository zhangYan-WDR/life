/* eslint-disable no-undef */
import { request } from '../../../utils/request';

const GESTURE_EMOJI = { ROCK: '✊', SCISSORS: '✌️', PAPER: '🖐️' };
const GESTURE_LABEL = { ROCK: '石头', SCISSORS: '剪刀', PAPER: '布' };

Page({
  data: {
    roomId: null,
    status: 'OPEN',
    participants: [],
    myGesture: null,
    loading: true,
    submitting: false,
    pollingTimer: null,
  },

  async onLoad(options) {
    const roomId = options.roomId;
    this.roomId = roomId;
    this.setData({ roomId });
    await this.loadStatus();
  },

  onUnload() {
    this.stopPolling();
  },

  async loadStatus() {
    try {
      const res = await request({ url: `/games/rps/${this.roomId}/status` });
      const me = res.participants.find(p => p.submitted && !this.data.myGesture
        ? false : p.userId === this.myUserId);
      this.setData({
        status: res.status,
        participants: res.participants.map(p => ({
          ...p,
          gestureEmoji: p.gesture ? GESTURE_EMOJI[p.gesture] : null,
          gestureLabel: p.gesture ? GESTURE_LABEL[p.gesture] : null,
        })),
        loading: false,
      });

      if (res.status === 'REVEALED') {
        this.stopPolling();
      } else if (this.data.myGesture && !this.pollingTimer) {
        this.startPolling();
      }
    } catch (e) {
      this.setData({ loading: false });
      wx.showToast({ title: '加载失败', icon: 'none' });
    }
  },

  async submitGesture(e) {
    const gesture = e.currentTarget.dataset.gesture;
    if (this.data.submitting || this.data.myGesture) return;

    this.setData({ submitting: true });
    try {
      await request({
        url: `/games/rps/${this.roomId}/gesture`,
        method: 'POST',
        data: { gesture },
      });
      this.setData({ myGesture: gesture, submitting: false });
      await this.loadStatus();
      if (this.data.status !== 'REVEALED') {
        this.startPolling();
      }
    } catch (e) {
      wx.showToast({ title: e.message || '提交失败', icon: 'none' });
      this.setData({ submitting: false });
    }
  },

  startPolling() {
    if (this.pollingTimer) return;
    this.pollingTimer = setInterval(() => this.loadStatus(), 3000);
  },

  stopPolling() {
    if (this.pollingTimer) {
      clearInterval(this.pollingTimer);
      this.pollingTimer = null;
    }
  },

  goBack() {
    wx.navigateBack();
  },
});
