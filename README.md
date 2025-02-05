![截图](screenshot/1.jpg)
![截图](screenshot/2.jpg)
![截图](screenshot/3.jpg)

电视越来越智能，但家里老人看电视越来越困难，电视家倒闭后，连我这个业内人士都不知道如何收看电视直播了。探索一番后，发现流行的TV直播软件都做得太复杂了（不是不好，而是功能太多了，比如我的电视以及一系列衍生出来的APP），其实电视直播最核心的功能就是视频播放器+直播源。所以本项目大删特删，不考虑字幕、点播、快进快退、节目单等复杂功能，专注于提供直播播放、收藏功能，考虑到当前视频源失效速度之快，以方便用户切换视频源为出发点，综合市场上热门的开源/闭源播放器，写了这个直播APP项目。本项目用到了开源/闭源共5个播放器，每个播放器都有自己的优缺点，可以自行选择下载使用，以下是各自特点：

①EXO播放器，开源，加载直播速度最快，但网络不好或视频源带宽不足的时候，开头可能会卡顿一下，且似乎只能使用硬解（官方最新的肯定是支持软解和硬解的，但本项目引入的是旧版其fongmi编译的so，在当贝投影仪上发现使用的是硬解，因为总是可以开启运动补偿）。

②IJK播放器，开源，加载速度一般，软解硬解都能开启，但当贝上无法使用运动补偿，提示当前没有正在播放的硬解视频。可能也是版本的原因，使用的是doikki的旧版库。

③KSY播放器，闭源，最初起源于IJK，但后面金山云团队深度改造，已经很难看到IJK的影子了，软解硬解都支持，加载速度也还不错。

④MPV播放器，开源，初次加载速度慢一些，但在带宽不足的情况下，缓冲做得最好，其他播放器都有卡顿的情况，MPV却丝滑如德芙，虽然它的api支持硬解，但开启后在当贝上无法开启运动补偿，提示当前没有正在播放的硬解视频，不知道是当贝的bug还是MPV的bug。

⑤VLC播放器，开源，加载速度不错，但概率性卡顿，这个问题从很旧的版本开始就存在，尤其是迅速退出播放再进入播放，重复多次。

格式支持：MPV=VLC>EXO=IJK>KSY

缓冲体验：MPV>VLC>EXO=KSY>IJK

如果你不需要用到运动补偿，首选MPV；
如果你想最快看到画面，选EXO；
如果你想综合能力最强，选VLC；

如果你想它是开源的，加载速度快，操作无卡顿，既可以硬解又可以软解，支持的格式又丰富，兼容当贝的运动补偿，那你这就是既要又要还要了，如果你真的这样想，就选VLC吧，但肯定无法做到各方面第一，如果你是开发者，可以慢慢调一下参数，或者能达到一个比较理想的效果。

使用APP之前，你需要按照以下步骤来制作属于自己的视频源：

第一步，先收集视频源，格式为：左边是名称，中间是英文逗号，右边是IPTV源链接，如果有多个视频源，则直接回车，如：
```
翡翠台,http://www.xxx.com/tv.m3u8
明珠台,http://www.xxx.com/tv.m3u8
...
```
然后把这些内容发布成一个txt直链，你也可以在部分博客上编写，前提是右键查看网页源代码要能看到上述填写的内容，且中间没有插入其他符号（很重要），因为我在APP里是按照正则表达式匹配的，所以一般市的m3u8视频源都可以正常解析。

假设你把上面的频道列表发布为 http://www.xxx.com/article1.txt ，重复这个步骤，你可以发布多个频道集合，比如再写一个文件，里面是卫视频道集合，发布为 http://www.xxx.com/article2.txt ：
```
广东卫视,http://www.xxx.com/tv.m3u8
湖南卫视,http://www.xxx.com/tv.m3u8
...
```

第二步，你要写一个频道集合的集合，格式相同，如：
```
香港电视,http://www.xxx.com/article1.txt
卫视集合,http://www.xxx.com/article2.txt
其他视源,http://www.xxx.com/article3.txt
我的喜欢,http://www.xxx.com/article4.txt
...
```

发布为：http://www.xxx.com/allinone.txt ，所以最终我们要在APP填写的地址是 http://www.xxx.com/allinone.txt ，他是频道集合的集合，也有些人把这个叫做组播，但无所谓，本项目里它就是集合的集合。如果你能在网上找到m3u视频源，只需要执行第二步，直接把视频源写入allinone里，起个名字即可，个人感觉这不是什么复杂的事情。如果你不知道怎么能获得txt直链或者找不到能看网页源代码的博客，可以试试 https://www.textdb.online ，或者自己部署彩虹网盘。你可以动态更新article1，article2，article3...的内容，切换视频源的时候，会自动获取最新的内容。

佛系开发，因上班没有太多空闲时间，为图便利本项目没有使用到ROOM数据库，所以有极小的概率丢失数据（比如点击收藏后秒退出APP），由于Android系统限制，APP可能无法在后台保存完整内容，所以导致只保存了一半数据。如果收藏对于你来说非常重要，那么请不要在点击收藏后秒退出APP。不喜勿喷，稍安勿躁，如果有不满意的地方，可以issue留言或者提交你的修改，我看到后会酌情采纳。
