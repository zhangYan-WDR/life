/* eslint-disable no-undef */

const COLORS = ['#e07830', '#c84848', '#4a90d9', '#7cb342', '#9c27b0', '#00acc1'];
const PHASE = { WAIT: 'wait', COUNTDOWN: 'countdown', RESULT: 'result' };

Page({
  data: {
    phase: PHASE.WAIT,
    fingers: [],      // [{id, x, y, color, colorIdx, eliminated}]
    countdown: 3,
    winnerId: null,
    winnerNum: null,
  },

  _activeTouches: null,   // Map<identifier, {x,y,colorIdx}>
  _colorCounter: 0,
  _countdownTimer: null,

  onLoad() {
    this._activeTouches = new Map();
  },

  onUnload() {
    this._clearTimer();
  },

  onTouchStart(e) {
    const { phase } = this.data;
    if (phase === PHASE.RESULT) return;

    for (const t of e.changedTouches) {
      if (!this._activeTouches.has(t.identifier)) {
        const colorIdx = this._colorCounter % COLORS.length;
        this._colorCounter++;
        this._activeTouches.set(t.identifier, {
          x: t.clientX,
          y: t.clientY,
          colorIdx,
        });
      }
    }

    this._syncFingers();

    if (phase === PHASE.WAIT && this._activeTouches.size >= 2) {
      this._startCountdown();
    }
  },

  onTouchMove(e) {
    for (const t of e.changedTouches) {
      if (this._activeTouches.has(t.identifier)) {
        const entry = this._activeTouches.get(t.identifier);
        entry.x = t.clientX;
        entry.y = t.clientY;
      }
    }
    this._syncFingers();
  },

  onTouchEnd(e) {
    const { phase } = this.data;
    if (phase === PHASE.RESULT) return;

    for (const t of e.changedTouches) {
      this._activeTouches.delete(t.identifier);
    }

    this._syncFingers();

    if (phase === PHASE.COUNTDOWN) {
      // 倒计时中有人离开 → 重置
      this._clearTimer();
      this.setData({ phase: PHASE.WAIT, countdown: 3 });
      wx.showToast({ title: '有人偷跑！重新开始', icon: 'none', duration: 1500 });
    } else if (phase === PHASE.WAIT && this._activeTouches.size < 2) {
      // 等待阶段手指不足，什么都不做
    }
  },

  _startCountdown() {
    this.setData({ phase: PHASE.COUNTDOWN, countdown: 3 });
    wx.vibrateShort({ type: 'medium' });

    this._countdownTimer = setInterval(() => {
      const next = this.data.countdown - 1;
      if (next <= 0) {
        this._clearTimer();
        this._pickWinner();
      } else {
        this.setData({ countdown: next });
        wx.vibrateShort({ type: 'light' });
      }
    }, 1000);
  },

  _pickWinner() {
    const ids = Array.from(this._activeTouches.keys());
    if (ids.length === 0) {
      this.setData({ phase: PHASE.WAIT, countdown: 3 });
      return;
    }

    const winnerId = ids[Math.floor(Math.random() * ids.length)];
    const winnerEntry = this._activeTouches.get(winnerId);
    // 编号 = 颜色索引 + 1（显示用）
    const winnerNum = (winnerEntry.colorIdx % COLORS.length) + 1;

    wx.vibrateLong();

    // 标记 eliminated
    const fingers = this.data.fingers.map(f => ({
      ...f,
      eliminated: f.id !== winnerId,
      winner: f.id === winnerId,
    }));

    this.setData({ phase: PHASE.RESULT, fingers, winnerId, winnerNum });
  },

  _syncFingers() {
    const fingers = [];
    let num = 1;
    for (const [id, entry] of this._activeTouches.entries()) {
      fingers.push({
        id,
        x: entry.x,
        y: entry.y,
        color: COLORS[entry.colorIdx % COLORS.length],
        num: num++,
        eliminated: false,
        winner: false,
      });
    }
    this.setData({ fingers });
  },

  _clearTimer() {
    if (this._countdownTimer) {
      clearInterval(this._countdownTimer);
      this._countdownTimer = null;
    }
  },

  reset() {
    this._clearTimer();
    this._activeTouches.clear();
    this._colorCounter = 0;
    this.setData({
      phase: PHASE.WAIT,
      fingers: [],
      countdown: 3,
      winnerId: null,
      winnerNum: null,
    });
  },
});
