/* eslint-disable no-undef */
import { request } from '../../../utils/request';

const SECTOR_COLORS = ['#e07830', '#c84848', '#8a6850', '#d4a060', '#a05838', '#c07048'];

Page({
  data: {
    members: [],
    canvasReady: false,
    spinning: false,
    currentRotation: 0,
    winner: null,
    showResult: false,
    newName: '',
    spinAnimation: null,
  },

  async onLoad() {
    try {
      const res = await request({ url: '/families/current' });
      const members = (res.members || []).map(m => ({ name: m.nickname, id: m.userId }));
      this.setData({ members });
    } catch (e) {
      wx.showToast({ title: '加载成员失败', icon: 'none' });
    }
  },

  onReady() {
    this.setData({ canvasReady: true });
    this.drawWheel();
  },

  drawWheel() {
    const { members } = this.data;
    if (!members.length) return;

    const ctx = wx.createCanvasContext('wheel-canvas', this);
    const size = 280;
    const cx = size / 2;
    const cy = size / 2;
    const r = size / 2 - 4;
    const sectorAngle = (2 * Math.PI) / members.length;

    members.forEach((m, i) => {
      const startAngle = i * sectorAngle - Math.PI / 2;
      const endAngle = startAngle + sectorAngle;

      ctx.beginPath();
      ctx.moveTo(cx, cy);
      ctx.arc(cx, cy, r, startAngle, endAngle);
      ctx.closePath();
      ctx.fillStyle = SECTOR_COLORS[i % SECTOR_COLORS.length];
      ctx.fill();

      // 扇区文字
      const midAngle = startAngle + sectorAngle / 2;
      const textR = r * 0.62;
      const tx = cx + textR * Math.cos(midAngle);
      const ty = cy + textR * Math.sin(midAngle);
      ctx.save();
      ctx.translate(tx, ty);
      ctx.rotate(midAngle + Math.PI / 2);
      ctx.fillStyle = '#fff';
      ctx.font = 'bold 22px PingFang SC';
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      const label = m.name.length > 4 ? m.name.slice(0, 4) + '…' : m.name;
      ctx.fillText(label, 0, 0);
      ctx.restore();
    });

    // 外圈
    ctx.beginPath();
    ctx.arc(cx, cy, r, 0, 2 * Math.PI);
    ctx.strokeStyle = '#fff';
    ctx.lineWidth = 6;
    ctx.stroke();

    // 中心圆
    ctx.beginPath();
    ctx.arc(cx, cy, 22, 0, 2 * Math.PI);
    ctx.fillStyle = '#fff';
    ctx.fill();

    ctx.draw(false, () => {
      wx.canvasToTempFilePath({
        canvasId: 'wheel-canvas',
        success: res => this.setData({ wheelImage: res.tempFilePath }),
      }, this);
    });
  },

  spin() {
    const { spinning, members, currentRotation } = this.data;
    if (spinning || members.length < 2) return;

    const idx = Math.floor(Math.random() * members.length);
    const sectorAngle = 360 / members.length;
    // 指针固定在顶部，转盘顺时针转，目标扇区中心对准顶部
    const targetOffset = 360 - (idx * sectorAngle + sectorAngle / 2);
    const totalDegrees = currentRotation + 360 * 6 + targetOffset - (currentRotation % 360);

    const anim = wx.createAnimation({ duration: 4500, timingFunction: 'ease-out' });
    anim.rotate(totalDegrees).step();
    this.setData({ spinning: true, showResult: false, spinAnimation: anim.export() });

    setTimeout(() => {
      this.setData({
        spinning: false,
        currentRotation: totalDegrees,
        winner: members[idx].name,
        showResult: true,
      });
    }, 4700);
  },

  closeResult() {
    this.setData({ showResult: false });
  },

  onNewNameInput(e) {
    this.setData({ newName: e.detail.value });
  },

  addMember() {
    const name = this.data.newName.trim();
    if (!name) return;
    const members = [...this.data.members, { name, id: Date.now() }];
    this.setData({ members, newName: '' }, () => this.drawWheel());
  },

  removeMember(e) {
    const id = e.currentTarget.dataset.id;
    const members = this.data.members.filter(m => m.id !== id);
    if (members.length < 2) {
      wx.showToast({ title: '至少保留 2 人', icon: 'none' });
      return;
    }
    this.setData({ members }, () => this.drawWheel());
  },
});
