/* eslint-disable no-undef */
const TOTAL_HOLES = 12;

// 预设孔位，分布在桶身的合理区域（百分比，避开箍和边缘）
const HOLE_POSITIONS = [
  { x: 18, y: 22 }, { x: 42, y: 18 }, { x: 68, y: 22 }, { x: 82, y: 30 },
  { x: 75, y: 48 }, { x: 55, y: 55 }, { x: 30, y: 52 }, { x: 14, y: 44 },
  { x: 22, y: 68 }, { x: 48, y: 72 }, { x: 70, y: 65 }, { x: 85, y: 55 },
];

Page({
  data: {
    totalHoles: TOTAL_HOLES,
    remainingHoles: TOTAL_HOLES,
    holes: [],
    currentPlayer: 0,
    exploded: false,
    explodedPlayer: -1,
    animating: false,
    barrelShaking: false,
  },

  onLoad() {
    const holes = HOLE_POSITIONS.map((pos, i) => ({
      id: i,
      x: pos.x,
      y: pos.y,
      stabbed: false,
      explodeSword: false,
    }));
    this.setData({ holes });
  },

  stab() {
    const { animating, exploded, remainingHoles, currentPlayer, holes } = this.data;
    if (animating || exploded) return;

    // 找第一个未插的孔
    const targetIdx = holes.findIndex(h => !h.stabbed);
    if (targetIdx === -1) return;

    const prob = 1 / remainingHoles;
    const triggered = Math.random() < prob;

    const nextHoles = holes.map((h, i) =>
      i === targetIdx ? { ...h, stabbed: true } : h
    );

    this.setData({ animating: true, holes: nextHoles });

    if (triggered) {
      wx.vibrateLong();
      this.setData({ barrelShaking: true });

      setTimeout(() => {
        // 让所有剑飞出
        const flyHoles = nextHoles.map(h => ({ ...h, explodeSword: h.stabbed }));
        this.setData({
          barrelShaking: false,
          exploded: true,
          explodedPlayer: currentPlayer,
          animating: false,
          remainingHoles: remainingHoles - 1,
          holes: flyHoles,
        });
      }, 700);
    } else {
      wx.vibrateShort({ type: 'light' });
      setTimeout(() => {
        this.setData({
          animating: false,
          remainingHoles: remainingHoles - 1,
          currentPlayer: 1 - currentPlayer,
        });
      }, 350);
    }
  },

  restart() {
    const holes = HOLE_POSITIONS.map((pos, i) => ({
      id: i,
      x: pos.x,
      y: pos.y,
      stabbed: false,
      explodeSword: false,
    }));
    this.setData({
      totalHoles: TOTAL_HOLES,
      remainingHoles: TOTAL_HOLES,
      holes,
      currentPlayer: 0,
      exploded: false,
      explodedPlayer: -1,
      animating: false,
      barrelShaking: false,
    });
  },
});
