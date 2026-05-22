/* eslint-disable no-undef */

const DICE_COUNT = 5;
const SHAKE_THRESHOLD = 15;
const SHAKE_COOLDOWN = 1200;

// 骰子每面的点位布局 [行][列] true=有点
const DOT_LAYOUTS = {
  1: [[false, false, false], [false, true,  false], [false, false, false]],
  2: [[true,  false, false], [false, false, false], [false, false, true ]],
  3: [[true,  false, false], [false, true,  false], [false, false, true ]],
  4: [[true,  false, true ], [false, false, false], [true,  false, true ]],
  5: [[true,  false, true ], [false, true,  false], [true,  false, true ]],
  6: [[true,  false, true ], [true,  false, true ], [true,  false, true ]],
};

Page({
  data: {
    dice: [],           // [{value, rolling, animDelay}]
    covered: true,      // 骰盅是否盖住
    shakeCount: 0,
    rolling: false,
    hasResult: false,
  },

  _lastAcc: { x: 0, y: 0, z: 0 },
  _lastShakeTime: 0,
  _shakeAudio: null,
  _openAudio: null,

  onLoad() {
    this._initDice();
    this._preloadAudio();
  },

  onShow() {
    this._startAccelerometer();
  },

  onHide() {
    this._stopAccelerometer();
  },

  onUnload() {
    this._stopAccelerometer();
    if (this._shakeAudio) { this._shakeAudio.destroy(); this._shakeAudio = null; }
    if (this._openAudio)  { this._openAudio.destroy();  this._openAudio = null; }
  },

  _initDice() {
    const dice = Array.from({ length: DICE_COUNT }, (_, i) => ({
      value: 1,
      rolling: false,
      animDelay: i * 80,
      dots: this._buildDots(1),
    }));
    this.setData({ dice, covered: true, shakeCount: 0, hasResult: false });
  },

  _buildDots(value) {
    const layout = DOT_LAYOUTS[value];
    const dots = [];
    for (let r = 0; r < 3; r++) {
      for (let c = 0; c < 3; c++) {
        dots.push({ show: layout[r][c], key: r * 3 + c });
      }
    }
    return dots;
  },

  _preloadAudio() {
    this._shakeAudio = wx.createInnerAudioContext();
    this._shakeAudio.src = '/audio/dice_shake.mp3';
    this._shakeAudio.volume = 0.9;

    this._openAudio = wx.createInnerAudioContext();
    this._openAudio.src = '/audio/dice_open.mp3';
    this._openAudio.volume = 0.9;
  },

  _startAccelerometer() {
    wx.startAccelerometer({ interval: 'game' });
    wx.onAccelerometerChange(res => this._onAcc(res));
  },

  _stopAccelerometer() {
    wx.offAccelerometerChange();
    wx.stopAccelerometer();
  },

  _onAcc(acc) {
    if (this.data.rolling) return;

    const { x: lx, y: ly, z: lz } = this._lastAcc;
    const dx = acc.x - lx, dy = acc.y - ly, dz = acc.z - lz;
    const delta = Math.sqrt(dx * dx + dy * dy + dz * dz);
    this._lastAcc = { x: acc.x, y: acc.y, z: acc.z };

    const now = Date.now();
    if (delta > SHAKE_THRESHOLD && now - this._lastShakeTime > SHAKE_COOLDOWN) {
      this._lastShakeTime = now;
      this._shake();
    }
  },

  _shake() {
    if (this.data.rolling) return;
    this.setData({ rolling: true, covered: true });

    // 音效 + 震动
    if (this._shakeAudio) {
      this._shakeAudio.stop();
      this._shakeAudio.play();
    }
    wx.vibrateLong();

    // 开始滚动动画
    const dice = this.data.dice.map(d => ({ ...d, rolling: true }));
    this.setData({ dice });

    // 1.4s 后停止，生成结果
    setTimeout(() => {
      const values = Array.from({ length: DICE_COUNT }, () =>
        Math.floor(Math.random() * 6) + 1
      );
      const newDice = values.map((v, i) => ({
        value: v,
        rolling: false,
        animDelay: this.data.dice[i].animDelay,
        dots: this._buildDots(v),
      }));
      this.setData({
        dice: newDice,
        rolling: false,
        covered: true,
        hasResult: true,
        shakeCount: this.data.shakeCount + 1,
      });
      wx.vibrateShort({ type: 'medium' });
    }, 1400);
  },

  // 手动摇按钮（模拟器用）
  manualShake() {
    this._shake();
  },

  peek() {
    if (!this.data.hasResult) return;
    if (this._openAudio) {
      this._openAudio.stop();
      this._openAudio.play();
    }
    wx.vibrateShort({ type: 'heavy' });
    this.setData({ covered: false });
  },

  cover() {
    this.setData({ covered: true });
  },

  reset() {
    this._initDice();
  },
});
