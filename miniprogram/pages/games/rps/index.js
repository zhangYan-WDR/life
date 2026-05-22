/* eslint-disable no-undef */
import { request } from '../../../utils/request';

Page({
  data: {
    members: [],
    selected: {},
    loading: true,
    creating: false,
  },

  async onLoad() {
    try {
      const res = await request({ url: '/families/current' });
      const selected = {};
      (res.members || []).forEach(m => { selected[m.userId] = true; });
      this.setData({ members: res.members || [], selected, loading: false });
    } catch (e) {
      wx.showToast({ title: '加载失败', icon: 'none' });
      this.setData({ loading: false });
    }
  },

  toggleMember(e) {
    const id = e.currentTarget.dataset.id;
    const selected = { ...this.data.selected, [id]: !this.data.selected[id] };
    this.setData({ selected });
  },

  async startGame() {
    const { selected, members, creating } = this.data;
    if (creating) return;

    const participantUserIds = members
      .filter(m => selected[m.userId])
      .map(m => m.userId);

    if (participantUserIds.length < 2) {
      wx.showToast({ title: '至少选择 2 人', icon: 'none' });
      return;
    }

    this.setData({ creating: true });
    try {
      const res = await request({
        url: '/games/rps',
        method: 'POST',
        data: { participantUserIds },
      });
      wx.navigateTo({ url: `/pages/games/rps/room?roomId=${res.roomId}` });
    } catch (e) {
      wx.showToast({ title: e.message || '创建失败', icon: 'none' });
    } finally {
      this.setData({ creating: false });
    }
  },
});
