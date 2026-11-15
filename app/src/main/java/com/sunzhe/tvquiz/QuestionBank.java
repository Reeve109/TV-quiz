package com.sunzhe.tvquiz;

import android.content.Context;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class QuestionBank {
    private static final List<Question> ALL = new ArrayList<>();
    private static int nextId = 1;

    /**
     * Online question bank for ONE grade, fetched from the upstream
     * open-source repo and cleaned on-device by QuestionFetcher. Per subject
     * it takes priority over the built-in bank; a subject with too few remote
     * questions (e.g. English in grades 1-2, which upstream does not have)
     * falls back to the built-in questions for that subject.
     */
    private static volatile List<Question> remote = null;
    private static volatile int remoteGrade = -1;

    /** Load the cached online bank for the currently selected grade, if any. */
    public static synchronized void init(Context ctx) {
        int g = new Prefs(ctx).getGrade();
        if (remote != null && remoteGrade == g) return;
        QuestionFetcher.loadCache(ctx, g);
    }

    static void setRemote(int grade, List<Question> qs) { remoteGrade = grade; remote = qs; }

    public static boolean isUsingRemote() { return remote != null; }

    public static int remoteGrade() { return remoteGrade; }

    /** Number of online questions for a grade (subject == null counts all subjects). */
    public static int remoteCount(int grade, String subject) {
        List<Question> r = remote;
        if (r == null) return 0;
        int n = 0;
        for (Question q : r)
            if (q.grade == grade && (subject == null || q.subject.equals(subject))) n++;
        return n;
    }

    public static int totalCount() { return active().size(); }

    private static List<Question> active() {
        List<Question> r = remote;
        return r != null ? r : ALL;
    }

    /** Pool for one grade+subject: online questions if at least 2, else built-in. */
    private static List<Question> poolFor(int grade, String subj) {
        List<Question> r = remote;
        List<Question> rp = new ArrayList<>();
        if (r != null)
            for (Question q : r) if (q.grade == grade && q.subject.equals(subj)) rp.add(q);
        if (rp.size() >= 2) return rp;
        List<Question> bp = new ArrayList<>();
        for (Question q : ALL) if (q.grade == grade && q.subject.equals(subj)) bp.add(q);
        return bp;
    }

    private static void add(int grade, String subj, String text, int correct, String... opts) {
        ALL.add(new Question(nextId++, grade, subj, text, correct, opts));
    }

    static {
        // ---------- Grade 1 Chinese (10) ----------
        add(1,"语文","“大”的反义词是什么？",1,"多","小","高","长");
        add(1,"语文","“上”的反义词是什么？",2,"左","右","下","前");
        add(1,"语文","太阳从哪个方向升起？",0,"东方","西方","南方","北方");
        add(1,"语文","“人”字加一笔，能变成哪个字？",1,"入","大","天","木");
        add(1,"语文","拼音“mā”中，“mā”是第几声？",0,"第一声","第二声","第三声","第四声");
        add(1,"语文","小鸟在天上做什么？",2,"游泳","跑步","飞","睡觉");
        add(1,"语文","下面哪个是水果？",1,"白菜","苹果","土豆","萝卜");
        add(1,"语文","“日”在天上指的是什么？",0,"太阳","月亮","星星","白云");
        add(1,"语文","“我爱中国”一共有几个字？",2,"3个","5个","4个","6个");
        add(1,"语文","春天过后是什么季节？",1,"秋天","夏天","冬天","春天");
        // ---------- Grade 1 Math (10) ----------
        add(1,"数学","5 + 3 = ?",2,"6","7","8","9");
        add(1,"数学","9 - 4 = ?",1,"4","5","6","3");
        add(1,"数学","7 + 6 = ?",2,"11","12","13","14");
        add(1,"数学","12 - 5 = ?",1,"6","7","8","9");
        add(1,"数学","8 + 8 = ?",2,"14","15","16","18");
        add(1,"数学","15 - 6 = ?",1,"8","9","10","7");
        add(1,"数学","3 + 9 = ?",2,"11","13","12","10");
        add(1,"数学","14 - 8 = ?",0,"6","7","5","8");
        add(1,"数学","10 - 3 = ?",1,"6","7","8","9");
        add(1,"数学","9 + 9 = ?",3,"16","17","19","18");
        // ---------- Grade 1 English (10) ----------
        add(1,"英语","“apple”是什么意思？",1,"香蕉","苹果","橙子","葡萄");
        add(1,"英语","“banana”是什么意思？",0,"香蕉","苹果","西瓜","梨");
        add(1,"英语","“red”是什么意思？",0,"红色","蓝色","绿色","黄色");
        add(1,"英语","“blue”是什么意思？",1,"红色","蓝色","黑色","白色");
        add(1,"英语","水果“orange”是什么意思？",2,"苹果","香蕉","橙子","桃子");
        add(1,"英语","“yellow”是什么意思？",3,"绿色","蓝色","红色","黄色");
        add(1,"英语","“green”是什么意思？",0,"绿色","黄色","紫色","粉色");
        add(1,"英语","“grape”是什么意思？",1,"草莓","葡萄","柠檬","芒果");
        add(1,"英语","“black”是什么意思？",2,"白色","灰色","黑色","蓝色");
        add(1,"英语","“white”是什么意思？",1,"黑色","白色","红色","绿色");

        // ---------- Grade 2 Chinese (10) ----------
        add(2,"语文","“大”的反义词是什么？",1,"多","小","高","长");
        add(2,"语文","“明”字是由哪两个字组成的？",0,"日和月","日和天","月和星","山和水");
        add(2,"语文","秋天来了，树叶会怎么样？",2,"变绿","开花","落下来","长大");
        add(2,"语文","下面哪个词语是描写春天的？",1,"大雪纷飞","鸟语花香","烈日炎炎","秋高气爽");
        add(2,"语文","一（ ）小狗，括号里应填哪个量词？",0,"只","条","头","匹");
        add(2,"语文","“你好吗”这句话最后应该用什么标点？",2,"句号。","逗号，","问号？","感叹号！");
        add(2,"语文","“跑”字的偏旁是什么？",1,"提手旁","足字旁","口字旁","单人旁");
        add(2,"语文","“开心”的近义词是什么？",0,"快乐","难过","生气","害怕");
        add(2,"语文","“高”的反义词是什么？",2,"大","长","矮","多");
        add(2,"语文","“雪白的（ ）”横线上最合适的是？",1,"天空","兔子","树叶","太阳");
        // ---------- Grade 2 Math (10) ----------
        add(2,"数学","27 + 15 = ?",1,"32","42","52","41");
        add(2,"数学","38 + 24 = ?",2,"52","61","62","72");
        add(2,"数学","45 - 18 = ?",1,"37","27","28","33");
        add(2,"数学","56 + 27 = ?",3,"73","82","93","83");
        add(2,"数学","70 - 35 = ?",0,"35","45","25","40");
        add(2,"数学","49 + 38 = ?",2,"77","86","87","97");
        add(2,"数学","84 - 39 = ?",1,"55","45","35","65");
        add(2,"数学","36 + 48 = ?",3,"74","94","76","84");
        add(2,"数学","50 - 26 = ?",0,"24","34","26","36");
        add(2,"数学","67 + 19 = ?",2,"76","85","86","96");
        // ---------- Grade 2 English (10) ----------
        add(2,"英语","“dog”是什么意思？",0,"狗","猫","鸟","鱼");
        add(2,"英语","“bird”是什么意思？",2,"狗","鱼","鸟","兔子");
        add(2,"英语","“fish”是什么意思？",1,"鸟","鱼","猫","鸭子");
        add(2,"英语","“tiger”是什么意思？",3,"狮子","大象","猴子","老虎");
        add(2,"英语","“monkey”是什么意思？",0,"猴子","熊猫","老虎","狼");
        add(2,"英语","“watermelon”是什么意思？",1,"葡萄","西瓜","草莓","菠萝");
        add(2,"英语","“rabbit”是什么意思？",2,"小狗","小猫","兔子","老鼠");
        add(2,"英语","“elephant”是什么意思？",1,"河马","大象","长颈鹿","熊");
        add(2,"英语","“panda”是什么意思？",0,"熊猫","企鹅","袋鼠","考拉");
        add(2,"英语","“cat”是什么意思？",3,"狗","猪","鸭","猫");

        // ---------- Grade 3 Chinese (10) ----------
        add(3,"语文","“床前明月光”的下一句是什么？",0,"疑是地上霜","举头望明月","低头思故乡","月是故乡明");
        add(3,"语文","成语“守株待兔”告诉我们什么道理？",1,"要爱护小动物","不能总想着不劳而获","兔子跑得快","种树很重要");
        add(3,"语文","“仔细”的近义词是什么？",2,"马虎","粗心","细心","随便");
        add(3,"语文","一（ ）桥，括号里应填哪个量词？",0,"座","条","张","个");
        add(3,"语文","下面哪个成语表示颜色很多？",1,"一清二白","五颜六色","黑白分明","青山绿水");
        add(3,"语文","古诗《春晓》的作者是谁？",2,"李白","杜甫","孟浩然","白居易");
        add(3,"语文","“喜欢”的反义词是什么？",1,"高兴","讨厌","开心","热爱");
        add(3,"语文","“假如”可以换成哪个词，意思差不多？",0,"如果","因为","所以","但是");
        add(3,"语文","下面哪个句子是比喻句？",1,"他跑得很快","弯弯的月亮像小船","今天天气很好","我喜欢读书");
        add(3,"语文","“地球”上我们生活的地方叫什么？",2,"太空","月球","陆地和海洋","太阳");
        // ---------- Grade 3 Math (10) ----------
        add(3,"数学","3 × 4 = ?",1,"7","12","14","9");
        add(3,"数学","6 × 7 = ?",2,"36","48","42","49");
        add(3,"数学","8 × 5 = ?",0,"40","35","45","30");
        add(3,"数学","45 + 38 = ?",3,"73","82","93","83");
        add(3,"数学","100 - 67 = ?",1,"43","33","23","37");
        add(3,"数学","7 × 9 = ?",2,"56","72","63","79");
        add(3,"数学","9 × 9 = ?",0,"81","72","99","18");
        add(3,"数学","4 × 9 = ?",1,"32","36","45","27");
        add(3,"数学","一星期有7天，3星期有多少天？",2,"14天","10天","21天","28天");
        add(3,"数学","5包糖，每包6颗，一共有多少颗？",1,"11颗","30颗","25颗","35颗");
        // ---------- Grade 3 English (10) ----------
        add(3,"英语","“I am a student.”是什么意思？",0,"我是一名学生","我是一名老师","我是他的朋友","我去上学");
        add(3,"英语","“This is my book.”是什么意思？",1,"那是我的书","这是我的书","这是你的书","这不是我的书");
        add(3,"英语","“He can run fast.”是什么意思？",2,"他能游得快","他走得快","他跑得很快","他跳得很高");
        add(3,"英语","“What is your name?”是什么意思？",0,"你叫什么名字？","你几岁了？","你在哪里？","你好吗？");
        add(3,"英语","“I like apples.”是什么意思？",1,"我不喜欢苹果","我喜欢苹果","我有苹果","我吃苹果");
        add(3,"英语","“The cat is under the table.”是什么意思？",3,"猫在桌子上","猫在桌子旁边","猫在椅子上","猫在桌子下面");
        add(3,"英语","“She is my teacher.”是什么意思？",0,"她是我的老师","他是我的老师","她是我的妈妈","她是我的同学");
        add(3,"英语","“We go to school.”是什么意思？",2,"我们回家","我们在学校","我们去上学","我们喜欢学校");
        add(3,"英语","“It is sunny today.”是什么意思？",1,"今天下雨","今天是晴天","今天很冷","今天刮风");
        add(3,"英语","“Thank you.”是什么意思？",0,"谢谢你","对不起","没关系","再见");

        // ---------- Grade 4 Chinese (10) ----------
        add(4,"语文","“著名”的近义词是什么？",0,"有名","陌生","普通","遥远");
        add(4,"语文","“温暖”的反义词是什么？",2,"炎热","暖和","寒冷","微风");
        add(4,"语文","成语“亡羊补牢”的意思是？",1,"羊丢了就不用再管了","出了问题及时补救，还不算晚","修羊圈要花很多钱","狼最喜欢吃羊");
        add(4,"语文","古诗《咏柳》的作者是谁？",3,"李白","杜甫","白居易","贺知章");
        add(4,"语文","“碧玉妆成一树高，万条垂下绿丝绦”描写的是哪种树？",2,"杨树","桃树","柳树","松树");
        add(4,"语文","成语“画蛇添足”告诉我们的道理是？",0,"做多余的事反而会把事情办坏","蛇本来没有脚","画画要画得像","做事要速度快");
        add(4,"语文","“高兴”的反义词是什么？",3,"开心","快乐","兴奋","难过");
        add(4,"语文","下面哪个词语是成语？",1,"高高兴兴","守株待兔","干干净净","来来往往");
        add(4,"语文","“春眠不觉晓”的下一句是什么？",2,"夜来风雨声","花落知多少","处处闻啼鸟","春风又绿江南岸");
        add(4,"语文","一（ ）牛，括号里应填哪个量词？",0,"头","只","匹","条");
        // ---------- Grade 4 Math (10) ----------
        add(4,"数学","125 × 8 = ?",1,"800","1000","900","1100");
        add(4,"数学","23 × 30 = ?",3,"609","6900","6090","690");
        add(4,"数学","4000 + 6000 = ?",0,"10000","1000","4600","6400");
        add(4,"数学","506070 读作？",2,"五千零六十万七十","五百万六千零七十","五十万六千零七十","五十万零六千七十");
        add(4,"数学","1/4 + 2/4 = ?",1,"3/8","3/4","2/4","1");
        add(4,"数学","5/7 − 2/7 = ?",0,"3/7","3/14","7/7","1/7");
        add(4,"数学","102 × 4 = ?",3,"428","404","4008","408");
        add(4,"数学","1 吨 = 多少千克？",2,"100","10","1000","10000");
        add(4,"数学","320 × 2 = ?",0,"640","604","620","630");
        add(4,"数学","3/5 里面有几个 1/5？",1,"5个","3个","15个","1个");
        // ---------- Grade 4 English (10) ----------
        add(4,"英语","“He ___ to school every day.”应该填哪个词？",2,"go","going","goes","went");
        add(4,"英语","“My mother is a ___.”（我妈妈是一名老师）应该填哪个词？",0,"teacher","apple","happy","run");
        add(4,"英语","“What time is it?”是在问什么？",3,"你叫什么名字","你几岁了","你好吗","现在几点");
        add(4,"英语","“There ___ a book on the desk.”（桌子上有一本书）应该填哪个词？",1,"are","is","am","be");
        add(4,"英语","单词“library”是什么意思？",2,"医院","学校","图书馆","公园");
        add(4,"英语","“I like playing ___.”（我喜欢踢足球）应该填哪个词？",3,"book","water","rice","football");
        add(4,"英语","“She has two ___.”（她有两只猫）应该填哪个词？",0,"cats","cat","cates","cat's");
        add(4,"英语","下面哪个是“星期一”？",1,"Sunday","Monday","Friday","Tuesday");
        add(4,"英语","“How many books do you have?”应该怎么回答？",3,"I'm fine.","I'm ten.","Good morning.","I have five books.");
        add(4,"英语","单词“hungry”是什么意思？",2,"开心的","累的","饿的","渴的");

        // ---------- Grade 5 Chinese (10) ----------
        add(5,"语文","成语“负荆请罪”讲的是谁向谁认错？",3,"刘备向诸葛亮","李白向杜甫","项羽向刘邦","廉颇向蔺相如");
        add(5,"语文","“桂林山水甲天下”中“甲”的意思是？",1,"穿着盔甲","第一","天下第一山","一种乐器");
        add(5,"语文","“海上生明月，天涯共此时”的作者是谁？",2,"李白","杜甫","张九龄","苏轼");
        add(5,"语文","下面哪句是比喻句？",0,"湖面像一面平静的镜子","他非常生气","今天天气晴朗","我们一起回家");
        add(5,"语文","《草船借箭》出自哪部名著？",1,"《西游记》","《三国演义》","《水浒传》","《红楼梦》");
        add(5,"语文","成语“守株待兔”中的“株”指的是什么？",3,"兔子","农夫","庄稼","树桩");
        add(5,"语文","“春风又绿江南岸”中“绿”字用得好，是因为它？",0,"把看不见的春风写得像能看见一样","是一个颜色词","读起来很响亮","这个字笔画少");
        add(5,"语文","下面哪句是拟人句？",2,"他跑得像风一样快","这本书很有趣","小鸟在枝头唱歌","妹妹很可爱");
        add(5,"语文","课文《落花生》的作者是谁？",1,"老舍","许地山","鲁迅","冰心");
        add(5,"语文","成语“程门立雪”是用来形容？",3,"天气寒冷","雪景很美","冬天要多穿衣服","对老师非常尊敬");
        // ---------- Grade 5 Math (10) ----------
        add(5,"数学","0.5 × 4 = ?",0,"2","0.2","20","2.5");
        add(5,"数学","3/4 + 1/2 = ?",2,"4/6","1","5/4","2/4");
        add(5,"数学","7.2 ÷ 8 = ?",3,"0.8","9","0.09","0.9");
        add(5,"数学","x + 5 = 12，x = ?",1,"17","7","6","5");
        add(5,"数学","1 − 3/8 = ?",0,"5/8","3/8","8/5","2/8");
        add(5,"数学","2.5 × 0.4 = ?",2,"10","0.1","1","100");
        add(5,"数学","5/6 − 1/3 = ?",1,"4/3","1/2","4/6","5/3");
        add(5,"数学","10 ÷ 4 = ?",3,"0.25","2","25","2.5");
        add(5,"数学","3.6 的 10 倍是多少？",2,"0.36","3.06","36","360");
        add(5,"数学","4x = 20，x = ?",0,"5","80","16","24");
        // ---------- Grade 5 English (10) ----------
        add(5,"英语","“tall”的比较级是哪个词？",1,"more tall","taller","tallest","talling");
        add(5,"英语","“I ___ TV last night.”（昨晚）应该填哪个词？",3,"watch","watches","watching","watched");
        add(5,"英语","“He ___ plays football on Sundays.”应该填哪个词？",0,"always","yesterday","tomorrow","last week");
        add(5,"英语","“My bag is bigger than ___.”应该填哪个词？",2,"you","your","yours","yourself");
        add(5,"英语","单词“yesterday”是什么意思？",3,"明天","今天","现在","昨天");
        add(5,"英语","“She ___ her homework yesterday.”应该填哪个词？",1,"do","did","does","doing");
        add(5,"英语","“Tom runs ___ than Jim.”应该填哪个词？",2,"fast","fastest","faster","more fast");
        add(5,"英语","“I usually go to school by bike.”中“usually”的意思是？",0,"通常","从不","昨天","已经");
        add(5,"英语","“Did you go to the park?”的正确回答是？",1,"No, I don't.","Yes, I did.","Yes, I do.","No, I'm not.");
        add(5,"英语","单词“always”是什么意思？",2,"有时","从不","总是","很少");

        // ---------- Grade 6 Chinese (10) ----------
        add(6,"语文","“伯牙鼓琴，志在登高山”出自哪部古书？",2,"《论语》","《孟子》","《吕氏春秋》","《史记》");
        add(6,"语文","《西游记》的作者是谁？",1,"罗贯中","吴承恩","施耐庵","曹雪芹");
        add(6,"语文","下面哪个句子没有语病？",0,"经过努力，他的成绩提高了。","通过这次比赛，使他信心大增。","我们讨论并听取了校长的报告。","他穿着一双新鞋子和一件大衣。");
        add(6,"语文","文言文“使弈秋诲二人弈”中“诲”的意思是？",3,"悔恨","后悔","聪明","教导");
        add(6,"语文","《水浒传》中绰号“及时雨”的是谁？",1,"武松","宋江","林冲","鲁智深");
        add(6,"语文","“两小儿笑曰：‘孰为汝多知乎？’”中“汝”的意思是？",2,"我","他","你","你们");
        add(6,"语文","成语“完璧归赵”的主人公是谁？",3,"廉颇","项羽","刘邦","蔺相如");
        add(6,"语文","“爆竹声中一岁除，春风送暖入屠苏”的作者是谁？",0,"王安石","苏轼","李白","杜甫");
        add(6,"语文","下面哪一项是病句？",2,"我们要保护环境。","今天天气晴朗。","他经常回忆过去的往事。","同学们都到齐了。");
        add(6,"语文","“日初出大如车盖”中“盖”指的是？",1,"盖子","车盖（车上的伞盖）","覆盖","遮盖");
        // ---------- Grade 6 Math (10) ----------
        add(6,"数学","1/2 × 1/3 = ?",3,"2/3","3/2","1","1/6");
        add(6,"数学","50 的 20% 是多少？",0,"10","20","5","25");
        add(6,"数学","3/4 ÷ 1/2 = ?",2,"3/8","2/3","3/2","1/2");
        add(6,"数学","半径 2 米的圆，周长约是多少？（π 取 3.14）",1,"6.28米","12.56米","25.12米","3.14米");
        add(6,"数学","3 : 5 的比值是？",3,"5/3","8","15","3/5");
        add(6,"数学","1/3 + 1/6 = ?",0,"1/2","2/9","1/9","2/3");
        add(6,"数学","80 千克的 25% 是多少千克？",1,"25","20","30","40");
        add(6,"数学","5/8 × 4 = ?",2,"5/32","9/8","5/2","5/12");
        add(6,"数学","一个圆的直径是 10 厘米，它的半径是多少？",0,"5厘米","20厘米","10厘米","2厘米");
        add(6,"数学","12 ÷ 3/4 = ?",3,"9","8","36","16");
        // ---------- Grade 6 English (10) ----------
        add(6,"英语","“I will ___ my grandma tomorrow.”应该填哪个词？",0,"visit","visited","visits","visiting");
        add(6,"英语","“The book is ___ the desk.”（书在桌子上）应该填哪个词？",3,"under","behind","between","on");
        add(6,"英语","“She is good ___ English.”应该填哪个词？",1,"in","at","on","for");
        add(6,"英语","“He is going to play basketball this weekend.”是什么意思？",2,"他昨天打篮球了","他正在打篮球","他这个周末打算打篮球","他不喜欢篮球");
        add(6,"英语","“What will the weather be like tomorrow?”是在问什么？",0,"明天的天气会怎么样","你明天去哪儿","明天是星期几","你喜欢什么天气");
        add(6,"英语","“Listen! The birds ___ singing.”应该填哪个词？",3,"is","am","be","are");
        add(6,"英语","“My home is next ___ the school.”应该填哪个词？",2,"in","on","to","of");
        add(6,"英语","单词“tomorrow”是什么意思？",1,"昨天","明天","今天","今晚");
        add(6,"英语","“We are going to have a picnic ___ Sunday.”应该填哪个词？",3,"in","at","by","on");
        add(6,"英语","“She ___ to the library yesterday.”应该填哪个词？",0,"went","go","goes","will go");
    }

    /** The built-in bank only (online bank not included). */
    public static List<Question> all() { return ALL; }

    public static int countForGrade(int grade) {
        int n = 0;
        for (String subj : new String[]{"语文", "数学", "英语"}) n += poolFor(grade, subj).size();
        return n;
    }

    /** Pick 2 per subject for grade (6 total), distinct within the round, avoiding recent ids if possible. */
    public static List<Question> pickQuiz(int grade, List<Integer> recent, Random rnd) {
        String[] subjects = {"语文","数学","英语"};
        List<Question> result = new ArrayList<>();
        for (String subj : subjects) {
            List<Question> pool = poolFor(grade, subj);
            List<Question> fresh = new ArrayList<>();
            for (Question q : pool) if (!recent.contains(q.id)) fresh.add(q);
            Collections.shuffle(fresh, rnd);
            List<Question> picked = new ArrayList<>();
            for (Question q : fresh) {
                if (picked.size() >= 2) break;
                picked.add(q);
            }
            if (picked.size() < 2) { // not enough fresh ones: top up from the rest of the pool
                List<Question> rest = new ArrayList<>(pool);
                Collections.shuffle(rest, rnd);
                for (Question q : rest) {
                    if (picked.size() >= 2) break;
                    if (!picked.contains(q)) picked.add(q);
                }
            }
            result.addAll(picked);
        }
        return result;
    }
}
