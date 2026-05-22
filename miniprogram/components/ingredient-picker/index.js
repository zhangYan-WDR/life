import request from "../../utils/request";

const PAGE_SIZE = 100;
const PRELOAD_THRESHOLD = 20;

Component({
  properties: {
    catalog: { type: Array, value: [] },
    value: { type: null, value: null },
    selectedItem: { type: Object, value: null },
    placeholder: { type: String, value: "请选择食材" },
    searchUrl: { type: String, value: "/ingredients/search" },
  },
  data: {
    open: false,
    keyword: "",
    searchResults: [],
    familyItems: [],
    systemCategories: [],
    treeLoaded: false,
    viewMode: "root", // root | subs | items
    currentCategory: null,
    currentSubcategory: null,
    currentSubs: [],
    currentItems: [],
    currentOffset: 0,
    currentHasMore: false,
    selected: null,
    searching: false,
    loadingTree: false,
    loadingItems: false,
    loadingMore: false,
    searchSeq: 0,
  },
  lifetimes: {
    attached() {
      this.setData({
        keyword: "",
        searchResults: [],
      });
    },
  },
  observers: {
    "catalog, value, selectedItem": function () {
      this.setData({ selected: this.computeSelected() });
    },
  },
  methods: {
    computeSelected() {
      const { catalog, value, selectedItem, familyItems, currentItems, searchResults } = this.data;
      if (selectedItem && (selectedItem.id != null || selectedItem.name)) {
        return selectedItem;
      }
      if (value == null) return null;
      const all = []
        .concat(catalog || [])
        .concat(familyItems || [])
        .concat(currentItems || [])
        .concat(searchResults || []);
      return all.find((it) => it && `${it.id}` === `${value}`) || null;
    },
    openPanel() {
      this._loading = false;
      this.setData({
        open: true,
        keyword: "",
        viewMode: "root",
        currentCategory: null,
        currentSubcategory: null,
        currentSubs: [],
        currentItems: [],
        currentOffset: 0,
        currentHasMore: false,
        loadingItems: false,
        loadingMore: false,
      });
      if (!this.data.treeLoaded) {
        this.loadCategoryTree();
      }
    },
    closePanel() {
      this._loading = false;
      this.setData({
        open: false,
        searching: false,
        keyword: "",
        searchResults: [],
        viewMode: "root",
        currentCategory: null,
        currentSubcategory: null,
        currentSubs: [],
        currentItems: [],
        loadingItems: false,
        loadingMore: false,
      });
    },
    onKeyword(e) {
      const keyword = (e && e.detail && e.detail.value) || "";
      this.setData({ keyword });
      const trimmed = keyword.trim();
      if (!trimmed) {
        this.setData({ searching: false, searchResults: [] });
        return;
      }
      this.searchRemote(trimmed);
    },
    clearKeyword() {
      this.setData({ keyword: "", searching: false, searchResults: [] });
    },
    async loadCategoryTree() {
      this.setData({ loadingTree: true });
      try {
        const response = await request({ url: "/ingredients/category-tree" });
        const familyItems = this.mergeUniqueItems((response && response.familyIngredients) || []);
        const systemCategories = (response && response.systemCategories) || [];
        this.setData({
          familyItems,
          systemCategories,
          treeLoaded: true,
          loadingTree: false,
        });
      } catch (error) {
        this.setData({ loadingTree: false });
        wx.showToast({ title: (error && error.message) || "加载分类失败", icon: "none" });
      }
    },
    chooseCategory(e) {
      const category = e.currentTarget.dataset.category;
      const node = (this.data.systemCategories || []).find((c) => c.category === category);
      if (!node) return;
      const subs = node.subCategories || [];
      if (subs.length === 1 && !subs[0].secondaryCategory) {
        this.setData({ currentCategory: category, currentSubs: subs });
        this.loadItemsByCategory(category, "", true);
        return;
      }
      this.setData({
        viewMode: "subs",
        currentCategory: category,
        currentSubs: subs,
      });
    },
    chooseSubcategory(e) {
      const secondaryCategory = (e.currentTarget.dataset.sub == null ? "" : e.currentTarget.dataset.sub) + "";
      console.log("[ingredient-picker] chooseSubcategory", this.data.currentCategory, "->", JSON.stringify(secondaryCategory));
      this.loadItemsByCategory(this.data.currentCategory, secondaryCategory, true);
    },
    async loadItemsByCategory(category, secondaryCategory, reset) {
      if (this._loading) return;
      this._loading = true;
      if (reset) {
        this.setData({
          loadingItems: true,
          viewMode: "items",
          currentCategory: category,
          currentSubcategory: secondaryCategory,
          currentItems: [],
          currentOffset: 0,
          currentHasMore: false,
        });
      } else {
        if (!this.data.currentHasMore) {
          this._loading = false;
          return;
        }
        this.setData({ loadingMore: true });
      }
      const offset = reset ? 0 : this.data.currentOffset;
      try {
        const response = await request({
          url: "/ingredients/by-category",
          data: { category, secondaryCategory: secondaryCategory || "", offset, limit: PAGE_SIZE },
        });
        const rawList = Array.isArray(response)
          ? response
          : (response && Array.isArray(response.items) ? response.items : []);
        const sliced = rawList.length > PAGE_SIZE ? rawList.slice(0, PAGE_SIZE) : rawList;
        const hasMore = rawList.length > sliced.length || sliced.length >= PAGE_SIZE;
        console.log("[ingredient-picker] by-category", category, secondaryCategory, "offset", offset, "got", rawList.length, "use", sliced.length);
        const items = this.mergeUniqueItems(sliced);
        const merged = reset ? items : (this.data.currentItems || []).concat(items);
        this.setData({
          currentItems: merged,
          currentOffset: offset + sliced.length,
          currentHasMore: hasMore,
          loadingItems: false,
          loadingMore: false,
        });
      } catch (error) {
        console.error("[ingredient-picker] by-category failed", error);
        this.setData({ loadingItems: false, loadingMore: false });
        wx.showToast({ title: (error && error.message) || "加载食材失败", icon: "none" });
      } finally {
        this._loading = false;
      }
    },
    onItemsScroll(e) {
      if (this.data.viewMode !== "items") return;
      if (this._loading || !this.data.currentHasMore) return;
      const detail = e && e.detail;
      if (!detail) return;
      const scrollTop = detail.scrollTop || 0;
      const scrollHeight = detail.scrollHeight || 0;
      if (scrollTop <= 0 || scrollHeight <= 0) return;
      // Only trigger near bottom (within 300px); requires user to actually scroll
      if (scrollHeight - scrollTop > 800) return;
      this.loadItemsByCategory(this.data.currentCategory, this.data.currentSubcategory || "", false);
    },
    backToRoot() {
      this.setData({
        viewMode: "root",
        currentCategory: null,
        currentSubcategory: null,
        currentSubs: [],
        currentItems: [],
      });
    },
    backToSubs() {
      this.setData({
        viewMode: "subs",
        currentSubcategory: null,
        currentItems: [],
      });
    },
    async searchRemote(keyword) {
      const currentSeq = this.data.searchSeq + 1;
      this.setData({ searchSeq: currentSeq, searching: true });
      try {
        const response = await request({
          url: this.data.searchUrl,
          data: { keyword, limit: 30 },
        });
        if (this.data.searchSeq !== currentSeq) return;
        const items = this.mergeUniqueItems((response && response.items) || []);
        this.setData({ searchResults: items, searching: false });
      } catch (error) {
        if (this.data.searchSeq !== currentSeq) return;
        this.setData({ searchResults: [], searching: false });
        wx.showToast({ title: error.message || "搜索食材失败", icon: "none" });
      }
    },
    mergeUniqueItems(items) {
      const map = {};
      const merged = [];
      (items || []).forEach((item) => {
        if (!item) return;
        const sourceType = item.sourceType || "SYSTEM";
        const key = `${sourceType}_${item.id}`;
        if (map[key]) return;
        map[key] = true;
        merged.push({ ...item, sourceType, uid: key });
      });
      return merged;
    },
    pick(e) {
      const id = e.currentTarget.dataset.id;
      const sourceType = e.currentTarget.dataset.sourceType;
      const pool = []
        .concat(this.data.searchResults || [])
        .concat(this.data.familyItems || [])
        .concat(this.data.currentItems || []);
      const item = pool.find((it) => `${it.id}` === `${id}` && `${it.sourceType}` === `${sourceType}`);
      if (!item) return;
      this.setData({
        open: false,
        keyword: "",
        searching: false,
        searchResults: [],
        viewMode: "root",
        currentCategory: null,
        currentSubcategory: null,
        currentSubs: [],
        currentItems: [],
        selected: item,
      });
      this.triggerEvent("change", { item });
    },
    stopProp() {},
  },
});
