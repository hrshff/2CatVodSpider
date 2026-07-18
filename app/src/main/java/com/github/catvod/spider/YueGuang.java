package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 月光影视 (www.shipian8.com)
 * TVBox Java Spider - FongMi 规范版
 */
public class YueGuang extends Spider {

    private static final String SITE_URL = "https://www.shipian8.com";

    // 月光影视需要桌面UA才能返回正常HTML
    private HashMap<String, String> getHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        headers.put("Referer", SITE_URL + "/");
        return headers;
    }

    private String fetch(String url) {
        return OkHttp.string(url, getHeaders());
    }

    private String fixUrl(String url) {
        if (TextUtils.isEmpty(url)) return "";
        if (url.startsWith("http")) return url;
        if (url.startsWith("//")) return "https:" + url;
        if (url.startsWith("/")) return SITE_URL + url;
        return SITE_URL + "/" + url;
    }

    private String extractId(String url) {
        if (TextUtils.isEmpty(url)) return "";
        Matcher m = Pattern.compile("/zwhsdt/(\\d+)\\.html").matcher(url);
        if (m.find()) return m.group(1);
        m = Pattern.compile("/zwhspy/(\\d+)-").matcher(url);
        if (m.find()) return m.group(1);
        return "";
    }

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context, extend);
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        classes.add(new Class("1", "电影"));
        classes.add(new Class("2", "电视剧"));
        classes.add(new Class("3", "综艺"));
        classes.add(new Class("4", "动漫"));
        classes.add(new Class("5", "短剧"));
        return Result.string(classes, new ArrayList<>());
    }

    @Override
    public String homeVideoContent() throws Exception {
        List<Vod> list = new ArrayList<>();
        HashSet<String> idSet = new HashSet<>();

        String html = fetch(SITE_URL + "/");
        Document doc = Jsoup.parse(html);

        // ========== 轮播图 ==========
        Elements banners = doc.select(".stui-vodlist__thumb.banner");
        for (Element banner : banners) {
            String href = fixUrl(banner.attr("href"));
            String title = banner.attr("title");
            String id = extractId(href);
            if (TextUtils.isEmpty(id) || TextUtils.isEmpty(title)) continue;

            String pic = "";
            String style = banner.attr("style");
            if (!TextUtils.isEmpty(style)) {
                Matcher m = Pattern.compile("url\\((.*?)\\)").matcher(style);
                if (m.find()) {
                    pic = m.group(1).trim();
                    if ((pic.startsWith("\"") && pic.endsWith("\"")) ||
                        (pic.startsWith("'") && pic.endsWith("'"))) {
                        pic = pic.substring(1, pic.length() - 1);
                    }
                }
            }

            if (idSet.contains(id)) continue;
            idSet.add(id);

            Vod vod = new Vod();
            vod.setVodId(id);
            vod.setVodName(title);
            vod.setVodPic(fixUrl(pic));
            list.add(vod);
        }

        // ========== 影片列表（首页用 .stui-vodlist__box） ==========
        Elements items = doc.select(".stui-vodlist__box");
        for (Element item : items) {
            Element link = item.selectFirst(".stui-vodlist__thumb");
            if (link == null) continue;

            String href = fixUrl(link.attr("href"));
            String title = link.attr("title");
            String id = extractId(href);
            if (TextUtils.isEmpty(id) || TextUtils.isEmpty(title)) continue;

            String pic = link.attr("data-original");
            if (TextUtils.isEmpty(pic)) pic = link.attr("src");

            String status = "";
            Element note = link.selectFirst(".pic-text");
            if (note != null) status = note.text().trim();

            if (idSet.contains(id)) continue;
            idSet.add(id);

            Vod vod = new Vod();
            vod.setVodId(id);
            vod.setVodName(title);
            vod.setVodPic(fixUrl(pic));
            vod.setVodRemarks(status);
            list.add(vod);
        }

        return Result.string(list);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        List<Vod> list = new ArrayList<>();
        int page;
        try {
            page = Integer.parseInt(pg);
        } catch (Exception e) {
            page = 1;
        }

        String url;
        if (page == 1) {
            url = SITE_URL + "/zwhstp/" + tid + ".html";
        } else {
            url = SITE_URL + "/zwhstp/" + tid + "-" + page + ".html";
        }

        String html = fetch(url);
        Document doc = Jsoup.parse(html);

        // ========== 分类页用 .stui-vodlist__item（不是 .stui-vodlist__box） ==========
        Elements items = doc.select(".stui-vodlist__item");
        for (Element item : items) {
            Element link = item.selectFirst(".stui-vodlist__thumb");
            if (link == null) continue;

            String href = fixUrl(link.attr("href"));
            String title = link.attr("title");
            String id = extractId(href);
            if (TextUtils.isEmpty(id) || TextUtils.isEmpty(title)) continue;

            String pic = "";
            String style = link.attr("style");
            if (!TextUtils.isEmpty(style)) {
                Matcher m = Pattern.compile("url\\((.*?)\\)").matcher(style);
                if (m.find()) {
                    pic = m.group(1).trim();
                    if ((pic.startsWith("\"") && pic.endsWith("\"")) ||
                        (pic.startsWith("'") && pic.endsWith("'"))) {
                        pic = pic.substring(1, pic.length() - 1);
                    }
                }
            }
            if (TextUtils.isEmpty(pic)) {
                pic = link.attr("data-original");
            }
            if (TextUtils.isEmpty(pic)) {
                pic = link.attr("src");
            }

            String status = "";
            Element note = link.selectFirst(".pic-text");
            if (note != null) status = note.text().trim();

            Vod vod = new Vod();
            vod.setVodId(id);
            vod.setVodName(title);
            vod.setVodPic(fixUrl(pic));
            vod.setVodRemarks(status);
            list.add(vod);
        }

        boolean hasNext = doc.select(".stui-page a").size() > 0 || list.size() >= 24;
        int pageCount = hasNext ? page + 1 : page;
        int total = list.size() > 0 ? page * 24 + 1 : 0;

        return Result.get().vod(list).page(page, pageCount, 24, total).string();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        if (ids == null || ids.isEmpty()) return "";
        String id = ids.get(0);

        String detailUrl = SITE_URL + "/zwhsdt/" + id + ".html";
        String html = fetch(detailUrl);
        Document doc = Jsoup.parse(html);

        Vod vod = new Vod();
        vod.setVodId(id);

        Element h1 = doc.selectFirst("h1");
        if (h1 != null) vod.setVodName(h1.text().trim());

        // 图片
        String pic = "";
        Element thumb = doc.selectFirst(".stui-content__thumb img");
        if (thumb != null) {
            pic = thumb.attr("data-original");
            if (TextUtils.isEmpty(pic)) pic = thumb.attr("src");
        }
        vod.setVodPic(fixUrl(pic));

        // 信息
        String director = "";
        String actor = "";
        String typeName = "";
        String area = "";
        String year = "";

        Elements dataRows = doc.select(".stui-content__detail p.data");
        for (Element row : dataRows) {
            String text = row.text();
            if (text.contains("导演")) {
                StringBuilder sb = new StringBuilder();
                for (Element a : row.select("a")) {
                    if (sb.length() > 0) sb.append(",");
                    sb.append(a.text().trim());
                }
                director = sb.toString();
            } else if (text.contains("主演")) {
                StringBuilder sb = new StringBuilder();
                for (Element a : row.select("a")) {
                    if (sb.length() > 0) sb.append(",");
                    sb.append(a.text().trim());
                }
                actor = sb.toString();
            } else if (text.contains("类型")) {
                typeName = text.replace("类型：", "").replace("类型:", "").trim();
                if (typeName.contains("地区")) {
                    typeName = typeName.substring(0, typeName.indexOf("地区")).trim();
                }
            } else if (text.contains("地区")) {
                area = text.replace("地区：", "").replace("地区:", "").trim();
                if (area.contains("年份")) {
                    area = area.substring(0, area.indexOf("年份")).trim();
                }
            } else if (text.contains("年份")) {
                year = text.replace("年份：", "").replace("年份:", "").trim();
            }
        }

        vod.setVodDirector(director);
        vod.setVodActor(actor);
        vod.setTypeName(typeName);
        vod.setVodArea(area);
        vod.setVodYear(year);

        // 简介
        Element descEl = doc.selectFirst(".desc.detail .detail-sketch");
        if (descEl != null) {
            vod.setVodContent(descEl.text().trim());
        } else {
            Element descFull = doc.selectFirst(".desc.detail .detail-content");
            if (descFull != null) vod.setVodContent(descFull.text().trim());
        }

        // 播放源
        List<String> playFroms = new ArrayList<>();
        List<String> playUrls = new ArrayList<>();

        Elements playlists = doc.select(".stui-content__playlist");
        for (int i = 0; i < playlists.size(); i++) {
            Element playlist = playlists.get(i);
            String tabName = "线路" + (i + 1);
            playFroms.add(tabName);

            Elements links = playlist.select("a");
            List<String> urls = new ArrayList<>();
            for (Element link : links) {
                String name = link.text().trim();
                String href = fixUrl(link.attr("href"));
                if (!TextUtils.isEmpty(name) && !TextUtils.isEmpty(href)) {
                    urls.add(name + "$" + href);
                }
            }
            playUrls.add(String.join("#", urls));
        }

        vod.setVodPlayFrom(String.join("$$$", playFroms));
        vod.setVodPlayUrl(String.join("$$$", playUrls));

        return Result.string(vod);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        if (TextUtils.isEmpty(id)) {
            return Result.get().url("").string();
        }

        String playUrl = id.startsWith("http") ? id : fixUrl(id);
        String html = fetch(playUrl);

        // 尝试提取 player_aaaa
        String playerJson = "";
        Matcher m = Pattern.compile("var player_aaaa\\s*=\\s*(\\{.*?\\});", Pattern.DOTALL).matcher(html);
        if (m.find()) {
            playerJson = m.group(1);
        } else {
            m = Pattern.compile("var player_\\w+\\s*=\\s*(\\{.*?\\});", Pattern.DOTALL).matcher(html);
            if (m.find()) playerJson = m.group(1);
        }

        HashMap<String, String> header = getHeaders();
        header.put("Origin", SITE_URL);
        header.put("Referer", playUrl);

        if (!TextUtils.isEmpty(playerJson)) {
            try {
                org.json.JSONObject player = new org.json.JSONObject(playerJson);
                String url = player.optString("url", "");
                int encrypt = player.optInt("encrypt", 0);

                if (encrypt == 1) {
                    url = java.net.URLDecoder.decode(url, "UTF-8");
                } else if (encrypt == 2) {
                    url = java.net.URLDecoder.decode(
                            new String(android.util.Base64.decode(url, android.util.Base64.DEFAULT)),
                            "UTF-8");
                }

                url = fixUrl(url);

                boolean isM3u8 = url.contains(".m3u8");
                boolean isMp4 = url.contains(".mp4");
                if (isM3u8 || isMp4) {
                    return Result.get().url(url).parse(0).header(header).string();
                }
            } catch (Exception e) {
                // ignore
            }
        }

        // iframe 回退
        Element iframe = Jsoup.parse(html).selectFirst("iframe");
        if (iframe != null) {
            String src = fixUrl(iframe.attr("src"));
            return Result.get().url(src).parse(1).header(header).string();
        }

        return Result.get().url(playUrl).parse(1).header(header).string();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        List<Vod> list = new ArrayList<>();
        int page;
        try {
            page = Integer.parseInt(pg);
        } catch (Exception e) {
            page = 1;
        }

        String encodedKey = URLEncoder.encode(key, "UTF-8");
        String url;
        if (page == 1) {
            url = SITE_URL + "/zwhssc/" + encodedKey + "-------------.html";
        } else {
            url = SITE_URL + "/zwhssc/" + encodedKey + "--------" + page + "---.html";
        }

        String html = fetch(url);
        Document doc = Jsoup.parse(html);

        // 搜索页可能用 .stui-vodlist__thumb 或 .v-thumb
        Elements items = doc.select(".stui-vodlist__thumb, .v-thumb");
        for (Element item : items) {
            String href = fixUrl(item.attr("href"));
            String title = item.attr("title");
            String id = extractId(href);
            if (TextUtils.isEmpty(id) || TextUtils.isEmpty(title)) continue;

            String pic = item.attr("data-original");
            if (TextUtils.isEmpty(pic)) pic = item.attr("src");

            String status = "";
            Element note = item.selectFirst(".pic-text");
            if (note != null) status = note.text().trim();

            Vod vod = new Vod();
            vod.setVodId(id);
            vod.setVodName(title);
            vod.setVodPic(fixUrl(pic));
            vod.setVodRemarks(status);
            list.add(vod);
        }

        boolean hasNext = doc.select(".stui-page a").size() > 0 || list.size() >= 24;
        int pageCount = hasNext ? page + 1 : page;
        int total = list.isEmpty() ? 0 : list.size();

        return Result.get().vod(list).page(page, pageCount, 24, total).string();
    }
}
