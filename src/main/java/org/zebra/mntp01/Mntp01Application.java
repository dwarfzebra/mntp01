package org.zebra.mntp01;


import io.itit.itf.okhttp.FastHttpClient;
import io.itit.itf.okhttp.callback.StringCallback;
import io.itit.itf.okhttp.util.FileUtil;
import okhttp3.Call;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Mntp01Application {

    static String  dir = "";

    //主题  本地路径
    public static void main(String[] args) throws Exception {

        String url = "http://mntp01.com";

        if(args.length==1){
            String subUrl = args[0].substring(args[0].indexOf("/"),args[0].lastIndexOf("/")+1);
            openContent(url,args[0],subUrl);
            return;
        }

        if(args.length!=2){
            System.out.println("输入参数有问题");
            return;
        }




        //主题中文名
        String name = args[0];


        dir = args[1];




        //获取网页信息
        String resp = FastHttpClient.get().url(url).build().execute().string();


        String pattern = "<div class=dh0.*</a";

        Pattern p = Pattern.compile(pattern);

        Matcher matcher = p.matcher(resp);

        //子目录
        List<String> keyList = new ArrayList<>();
        List<String> subUrlList = new ArrayList<>();

        //跳过首页
        matcher.find();
        while (matcher.find()) {

            String key = "";

            String subUrl = "";

            String theme = matcher.group();
            String urlP = "href=\".*\"";
            String keyP1 = ">.*</a";
            String keyP2 = "b>.*</b";

            Pattern urlPattern = Pattern.compile(urlP);
            Pattern keyPattern1 = Pattern.compile(keyP1);
            Pattern keyPattern2 = Pattern.compile(keyP2);

            Matcher urlM = urlPattern.matcher(theme);
            Matcher keyM1 = keyPattern1.matcher(theme);
            Matcher keyM2 = keyPattern2.matcher(theme);
            if (urlM.find()) {
                subUrl = urlM.group().replace("href=\"", "").replace("\"", "");
            }
            if (keyM2.find()) {
                key = keyM2.group().replace("b>", "").replace("</b", "");
            } else {
                if (keyM1.find()) {
                    String value = keyM1.group();
                    key = value.substring(value.lastIndexOf(">") + 1, value.lastIndexOf("<"));
                }
            }
            keyList.add(key);
            subUrlList.add(subUrl);
        }


        //  收集数据  进行分组
        if(!keyList.contains(name)){
            System.out.println("为搜索到此主题，请输入完整主题名");
            System.out.println("目前收录以下:\n");
            keyList.stream().forEach((k)-> System.out.println(k));
            return;
        }

        //  根据所求找到所在分组
        String subUrl = subUrlList.get(keyList.indexOf(name));

        String subPage = url + subUrl;

        System.out.println("准备搜索"+subPage);
        String subPageHtml = FastHttpClient.get().url(subPage).build().execute().string();

        //尾页正则
        String lastP = "href=\"\\D*?index[1-9]{2}.*</a";
        //页数正则
        String pageP = "href=.+?>[0-9]+</a";

        Pattern lastPattern = Pattern.compile(lastP);
        Pattern pagePattern = Pattern.compile(pageP);

        Matcher lastM = lastPattern.matcher(subPageHtml);
        Matcher pageM = pagePattern.matcher(subPageHtml);


        //页面集合
        List<String> pageUrl = new ArrayList<>();

        if (lastM.find()) {
            String value = lastM.group();
            String count = value.substring(value.indexOf("index") + 5, value.lastIndexOf(".html"));
            Integer c = Integer.valueOf(count);
            pageUrl.add(subUrl);
            for (int i = 2; i <= c; i++) {
                pageUrl.add(subUrl+"index" + i + ".html");
            }
        } else {
            pageUrl.add(subUrl);
            pageM.find();
            while (pageM.find()) {
                String value = pageM.group();
                pageUrl.add(value.substring(value.indexOf("href=") + 6, value.lastIndexOf("\"")));
            }
        }


        // 获取分组页面数据
        pageUrl.stream().forEach(page -> {
            //异步get
            FastHttpClient.get().url(url + page).build().
                    executeAsync(new StringCallback() {
                        @Override
                        public void onFailure(Call call, Exception e, int id) {
                            System.out.println("获取" + url + page + "数据失败");
                        }

                        @Override
                        public void onSuccess(Call call, String response, int id) {

                            //内容正则
                            String contentP = subUrl+"\\D{2,20}html";
                            Pattern contentPattern = Pattern.compile(contentP);
                            Matcher contentM = contentPattern.matcher(response);

                            List<String> contentList = new ArrayList<>();
                            while (contentM.find()) {
                                String value = contentM.group();
                                contentList.add(value);
                            }


                            contentList.stream().forEach(c -> {
                                openContent(url,c,subUrl);
                            });


                        }
                    });
        });
    }


    static void openContent(String url, String c,String subUrl) {
        FastHttpClient.get().url(url + c).build().
                executeAsync(new StringCallback() {
                    @Override
                    public void onFailure(Call call, Exception e, int id) {
                        System.out.println(e);
                    }

                    @Override
                    public void onSuccess(Call call, String response, int id) {
                        //页数正则
                        String pageNumP = "href=.+?>[0-9]+</a";
                        Pattern pageNumPattern = Pattern.compile(pageNumP);
                        Matcher pageNumM = pageNumPattern.matcher(response);

                        List<String> pageList = new ArrayList<>();
                        pageList.add(subUrl);
                        pageNumM.find();
                        while (pageNumM.find()) {
                            String value = pageNumM.group();
                            //正则
                            pageList.add(value.substring(value.indexOf("href=") + 6, value.lastIndexOf("\"")));
                        }

                        //打开每一页 进行存储其中的数据
                        pageList.stream().forEach(p -> {
                            openImg(url,p,subUrl);
                        });
                    }
                });
    }


   static void openImg(String url, String p,String subUrl) {
        //异步get
        FastHttpClient.get().url(url + p).build().
                executeAsync(new StringCallback() {
                    @Override
                    public void onFailure(Call call, Exception e, int id) {
                        //
                    }

                    @Override
                    public void onSuccess(Call call, String response, int id) {
                        //图片正则
                        String imgP = "onload.*jpg.*>";
                        Pattern imgPattern = Pattern.compile(imgP);
                        Matcher imgM = imgPattern.matcher(response);
                        while (imgM.find()) {
                            String value = imgM.group();
                            String imgUrl = value.substring(value.indexOf("src=") + 5, value.lastIndexOf("jpg")+3);

                            //查询目录是否存在 若不存在创建
                            String dirPath =dir + subUrl+p.substring(0,p.lastIndexOf("_"));
                            File dir = new File(dirPath);
                            if (!dir.exists()) {
                                dir.mkdirs();
                            }
                            String fileName = imgUrl.substring(imgUrl.lastIndexOf("/") + 1);

                            String imgIp = "https://www.mntp01.com";

                            System.out.println("已打开"+url+p);

                            System.out.println("下载"+imgIp+imgUrl);
                            InputStream is = null;
                            try {
                                is = FastHttpClient.newBuilder().connectTimeout(30, TimeUnit.SECONDS).readTimeout(60,TimeUnit.SECONDS)
                                        .build().get()
                                        .addHeader("referer",imgIp+imgUrl)
                                        .addHeader("user-agent","Mozilla/5.0 (iPhone; CPU iPhone OS 13_2_3 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/13.0.3 Mobile/15E148 Safari/604.1")
                                        .url(imgIp+imgUrl).build()
                                        .execute().byteStream();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            try {
                                FileUtil.saveContent(is, new File(dirPath + "/" + fileName));
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                });
    }
}
