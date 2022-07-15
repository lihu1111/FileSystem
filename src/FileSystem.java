import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Scanner;


public class FileSystem {
    public final static String DISK_PATH = "myDisk.disk";
    public final static int DISK_SIZE = 64 * 1024 * 1024;
    public final static int BLOCK_SIZE = 8 * 1024;
    //当前目录
    public static FCB curDir = null;
    public static User curUser = null;//登录用户
    public static ArrayList<User> users = new ArrayList<>();//所有已注册用户

    public static void main(String[] args) {
        initDisk();//初始化虚拟磁盘
        FAT.readFAT();//将磁盘的FAT表读出来
        Block.readFree();//把磁盘中的空闲块读出来
        initUser();//用户初始化
        loginPage();
        Scanner sc = new Scanner(System.in);
        while (true) {
            System.out.print(getPath() + ">");
            String instruction = sc.nextLine();
            instruction = instruction.trim();
            if (instruction.equals("help")) {
                help();
            } else if (instruction.equals("quit")) { //退出前先把信息重新写回磁盘，方面以后启动的时候可以直接读取
                FAT.writeFAT();
                Block.writeFree();
                System.out.println("退出成功！");
                break;
            } else if (instruction.startsWith("mkdir")) {
                String[] inss = instruction.split(" ");
                if (inss.length == 1) {
                    System.out.println("指令存在错误，请重新输入:");
                } else {
                    String filename = inss[1];
                    if (curDir.createFCB(filename, "文件夹") != null) {
                        System.out.println("文件夹" + filename + "创建成功");
                    }
                }
            } else if (instruction.startsWith("deldir")) {
                deldir(instruction);
            } else if (instruction.startsWith("dir")) {
                dir(instruction);
            } else if (instruction.startsWith("treedir")) {
                treeDir(instruction);
            } else if (instruction.startsWith("cd")) {
                cdDir(instruction);
            } else if (instruction.startsWith("create")) {
                create(instruction);
            } else if (instruction.startsWith("open")) {
                openFile(instruction, true);
            } else if (instruction.startsWith("close")) {
                closeFile(instruction);
            } else if (instruction.startsWith("read")) {
                String result = readFile(instruction);
                if (result != null) {
                    System.out.println(result);
                }
            } else if (instruction.startsWith("write")) {
                writeFile(instruction, null);
            } else if (instruction.startsWith("copy")) {
                copyFile(instruction);
            } else if (instruction.startsWith("xcopydir")) {
                xcopyDir(instruction);
            } else if (instruction.startsWith("import")) {
                importFile(instruction);
            } else if (instruction.startsWith("export")) {
                exportFile(instruction);
            } else {
                System.out.println("指令存在错误，请重新输入:");
            }
        }
    }

    public static void initDisk() {
        //初始化虚拟磁盘
        File file = new File(DISK_PATH);
        //如果虚拟磁盘不存在则新建一个
        if (!file.exists()) {
            try {
                RandomAccessFile rw = new RandomAccessFile(DISK_PATH, "rw");
                byte[] bytes = new byte[DISK_SIZE];
                rw.write(bytes);
                rw.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
            //初始化FAT表 前四个块存放FAT表,第4号块放位示图，第5号块放用户信息
            try {
                RandomAccessFile rw = new RandomAccessFile(DISK_PATH, "rw");
                rw.seek(0);
                for (int i = 0; i < BLOCK_SIZE; i++) {
                    if (i == 0)
                        rw.writeInt(1);
                    else if (i == 1)
                        rw.writeInt(2);
                    else if (i == 2)
                        rw.writeInt(-1);
                    else if (i == 3)
                        rw.writeInt(-1);
                    else if (i == 4)
                        rw.writeInt(-1);
                    else if (i == 5)
                        rw.writeInt(-1);
                    else
                        rw.writeInt(-2);
                }
                rw.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            //初始化空闲块 4
            try {
                RandomAccessFile rw = new RandomAccessFile(DISK_PATH, "rw");
                rw.seek(BLOCK_SIZE * 4);
                byte[] bytes = new byte[BLOCK_SIZE];
                for (int i = 0; i < BLOCK_SIZE; i++) {
                    if (i == 0 || i == 1 || i == 2 || i == 3 || i == 4 || i == 5) //0,1,2,3存FAT表，4存空闲表即位示图，5存用户表
                        bytes[i] = 1;
                    else
                        bytes[i] = 0;
                }
                rw.write(bytes);
                rw.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            //初始化用户块 5
            try {
                RandomAccessFile rw = new RandomAccessFile(DISK_PATH, "rw");
                rw.seek(BLOCK_SIZE * 5);
                rw.writeInt(0); //当前0个用户
                rw.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            //将除了前6块，每块第一个数写入本块内存的文件数量
            for (int i = 6; i < BLOCK_SIZE; i++) {
                writeInt(0, i * BLOCK_SIZE);
            }
        }

    }

    /**
     * 获得当前目录的路径
     *
     * @return
     */
    public static String getPath() {
        String path = "";
        FCB temp = curDir;
        if (temp == null) //没有用户登录
            path = "";
        else {
            if (temp.parent == null) { //当前目录为顶级目录即用户目录
                path = temp.name + ":";
                temp = temp.parent;  //temp为null
            } else {  //当前目录不是顶级目录
                path = temp.name;
                temp = temp.parent;//temp不为null
            }
            while (temp != null) {  //判断是否还有更高级的目录
                if (temp.parent != null) //上面还有更高级的目录
                    path = temp.name + "\\" + path;
                else
                    path = temp.name + ":\\" + path;
                temp = temp.parent;
            }
        }
        return path;
    }

    public static boolean login(String username, String password) {
        //寻找是否注册
        for (int i = 0; i < users.size(); i++) {
            User user = users.get(i);
            if (user.username.equals(username)) {
                if (user.password.equals(password)) {
                    System.out.println("登陆成功!");
                    curUser = user;
                    curDir = user.userFCB;
                    curUser.init();
                    return true;
                } else {
                    System.out.println("密码输入错误!");
                    return false;
                }
            }
        }
        System.out.println("系统中无该用户!");
        return false;
    }

    public static boolean register(String username, String password) {
        byte[] name = username.getBytes(StandardCharsets.UTF_8);
        if (name.length > 32) {
            System.out.println("用户名过长！");
            return false;
        }
        byte[] pass = password.getBytes(StandardCharsets.UTF_8);
        if (pass.length > 32) {
            System.out.println("密码过长！");
            return false;
        }
        //匹配所有用户,看是否重名
        for (int i = 0; i < users.size(); i++) {
            User user = users.get(i);
            if (user.username.equals(username)) {
                System.out.println("该用户已存在！");
                return false;
            }
        }
        //开始注册新用户
        // 获取磁盘中用户个数
        int num = FileSystem.readInt(BLOCK_SIZE * 5);
        num++;
        //8 * 1024 / 72  = 113.7
        //为了稳妥，最多110个用户
        if (num > 110) {
            System.out.println("用户个数已达上限");
            return false;
        }
        //为其FCB表分配一块空间
        ArrayList<Integer> free = Block.findFree(1);
        if (free.size() == 0) {
            System.out.println("磁盘空间已满");
            return false;
        }
        //创建新用户
        User user = new User(username, password, 0, free.get(0));
        users.add(user);
        //将新用户写入磁盘
        try {
            RandomAccessFile rw = new RandomAccessFile(DISK_PATH, "rw");
            //一个用户信息占  72B
            rw.skipBytes(BLOCK_SIZE * 5 + 4 + (num - 1) * 72);
            name = Arrays.copyOf(name, 32);
            rw.write(name);
            pass = Arrays.copyOf(pass, 32);
            rw.write(pass);
            //文件长度
            rw.writeInt(0);
            //用户文件位置
            rw.writeInt(free.get(0));
            rw.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        FileSystem.writeInt(num, BLOCK_SIZE * 5);//更改后的用户个数写入磁盘
        return true;
    }

    public static void initUser() {
        int num = FileSystem.readInt(BLOCK_SIZE * 5);//获取磁盘中用户个数
        try {
            RandomAccessFile rw = new RandomAccessFile(FileSystem.DISK_PATH, "rw");
            rw.skipBytes(BLOCK_SIZE * 5 + 4);
            for (int i = 0; i < num; i++) {  //获取磁盘中所有用户的详细信息
                byte[] name = new byte[32];
                rw.read(name);
                String username = new String(name, StandardCharsets.UTF_8).trim();
                byte[] pass = new byte[32];
                rw.read(pass);
                String password = new String(pass, StandardCharsets.UTF_8).trim();
                int totallength = rw.readInt();
                int userFCBaddr = rw.readInt();
                User user = new User(username, password, totallength, userFCBaddr);
                users.add(user);
            }
            rw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }


    public static void create(String instruction) {
        String[] inss = instruction.split(" ");
        if (inss.length == 1) {
            System.out.println("输入的指令有误！");
            return;
        }
        //在当前目录下创建文件
        if (!instruction.contains(curUser.username + ":")) {
            String filename = inss[1];
            if (curDir.createFCB(filename, "文件") != null) {
                System.out.println("文件" + filename + "创建成功");
                return;
            }
        } else if (inss.length == 3) {  // create +路径 +文件名 create l:\dir1 file1.txt
            String filename = inss[2];
            FCB curDir = cd(inss[1]);
            if (curDir.createFCB(filename, "文件") != null) {
                System.out.println("文件" + filename + "创建成功");
                return;
            }
        } else {
            System.out.println("输入的指令有误！");
        }
    }

    public static void treeDir(String instruction) {
        String[] inss = instruction.split(" ");
        if (inss.length == 1) {
            System.out.println("输入的指令有误！");
            return;
        }
        //相对路径 处理
        if (!inss[1].contains(curUser.username + ":")) {
            inss[1] = getPath() + "\\" + inss[1];
        }
        FCB targetDir = cd(inss[1]); //目标目录
        if (targetDir == null) {
            System.out.println("未找到指定路径的目录!");
            return;
        }
        System.out.println(targetDir.name + " " + targetDir.type);
        //递归打印子目录
        printDir(targetDir, "\t");
    }

    /**
     * 递归打印文件目录
     * 前序遍历
     *
     * @param targetDir  要打印的目标路径
     * @param blankSpace 文件名前显示的空格
     */
    public static void printDir(FCB targetDir, String blankSpace) {
        if (targetDir.childFCB == null ||targetDir.childFCB.size() == 0) {
            return;
        }
        for (int i = 0; i < targetDir.childFCB.size(); i++) {
            FCB fcb = targetDir.childFCB.get(i);
            String name = blankSpace + fcb.name;
            if (fcb.type.equals("文件")) {
                System.out.println(name + " " + fcb.type);
            } else {
                System.out.println(name + " " + fcb.type);
                //进入下一层目录  空格+1
                printDir(fcb, blankSpace + "\t");
            }

        }
    }

    public static void dir(String instruction) {
        String[] is = instruction.split(" ");
        System.out.println("文件名\t\t" + "类型\t\t" + "大小\t" + "创建时间 " + "\t\t\t" + "最近修改时间");
        ArrayList<FCB> file = new ArrayList<>();
        if (instruction.equals("dir"))  //在当前路径下执行
            file = curDir.childFCB;
        else if (is[1].equals(curUser.username + ":")) {  //dir主目录即最高级用户目录
            file = curUser.userFCB.childFCB;
        } else {  //给定的绝对路径
            String[] path = is[1].split("\\\\"); //以”\“分割给定路径
            if (!path[0].equals(curUser.username + ":")) { //目录不符合则路径错误
                System.out.println("路径错误");
                return;
            }
            FCB fcb = curUser.userFCB;
            for (int i = 1; i < path.length; i++) { //遍历给定路径的各个目录
                boolean flag = false;
                if (fcb.childFCB != null) {
                    for (int j = 0; j < fcb.childFCB.size(); j++) {
                        if (fcb.childFCB.get(j).name.equals(path[i]) && fcb.childFCB.get(j).type.equals("文件夹")) {
                            if (i == path.length - 1) { //已经遍历到最后一个目录了
                                file = fcb.childFCB.get(j).childFCB;
                                flag = true;
                                break;
                            } else { //后面还有更下级的目录
                                fcb = fcb.childFCB.get(j);
                                flag = true;
                                break;
                            }
                        }
                    }
                }
                if (flag == false) {
                    System.out.println("路径错误!");
                    return;
                }
            }
        }
        for (int i = 0; i < file.size(); i++) {
            FCB f = file.get(i);
            if (f.type.equals("文件夹")) {
                System.out.println(f.name + "\t\t\t" + f.type + "\t" + f.length + "\t" + f.createTime + "\t" + f.lastUpdateTime + "\t");
            } else {
                String[] split = f.name.split("\\.");
                System.out.println(f.name + "\t\t" + split[1] + f.type + "\t" + f.length + "\t" + f.createTime + "\t" + f.lastUpdateTime + "\t");
            }
        }
    }

    public static void deldir(String instruction) {
        String[] inss = instruction.split(" ");
        if (inss.length == 1) {
            System.out.println("输入的指令有误！");
            return;
        }
        if (!inss[1].contains(curUser.username + ":")) { //代表删除当前目录下的目录
            inss[1] = getPath() + "\\" + inss[1];
        }
        FCB delfcb = cd(inss[1]); //要删除的目录的fcb
        if (delfcb == null) {
            System.out.println("未找到指定路径的目录!");
            return;
        }
        if (delfcb.childFCB.size() != 0) {
            System.out.println("不是空目录，删除失败");
            return;
        }
        FCB parent = delfcb.parent; //得到空目录的上一级目录
        //先把空目录所占的空闲块释放
        Block.blockInfo[delfcb.startAdd] = 0;
        //修该父级目录fcb表
        parent.childFCB.remove(delfcb);
        //将修改后的父级目录fcb表写入内存
        FileSystem.writeInt(parent.childFCB.size(), BLOCK_SIZE * parent.startAdd);//修改父级目录文件个数
        try {
            //更新
            RandomAccessFile rw = new RandomAccessFile(DISK_PATH, "rw");
            rw.skipBytes(BLOCK_SIZE * parent.startAdd + 4);
            for (int i = 0; i < parent.childFCB.size(); i++) {  //更新父级目录的fcb表
                FCB fcb = parent.childFCB.get(i);
                byte[] fileName = fcb.name.getBytes(StandardCharsets.UTF_8);
                fileName = Arrays.copyOf(fileName, 32);
                rw.write(fileName);
                byte[] fileType = fcb.type.getBytes(StandardCharsets.UTF_8);
                fileType = Arrays.copyOf(fileType, 32);
                rw.write(fileType);
                rw.writeInt(fcb.length);
                rw.writeInt(fcb.startAdd);
                byte[] createTime = fcb.createTime.getBytes(StandardCharsets.UTF_8);
                createTime = Arrays.copyOf(createTime, 16);
                rw.write(createTime);
                byte[] updateTime = fcb.lastUpdateTime.getBytes(StandardCharsets.UTF_8);
                updateTime = Arrays.copyOf(updateTime, 16);
                rw.write(updateTime);
            }
            rw.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        //同时也要修改父级目录的更新时间
        //在当前目录下创建文件或文件夹成功后，需要更改当前目录fcb的更新时间
        Date date = new Date();
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        String time = formatter.format(date);
        parent.lastUpdateTime = time;
        FCB parent2 = parent.parent;
        if (parent2 != null) {
            int cnt = FileSystem.readInt(BLOCK_SIZE * parent2.startAdd);//获取父级目录下文件个数
            try {
                RandomAccessFile rw = new RandomAccessFile(DISK_PATH, "rw");
                rw.skipBytes(BLOCK_SIZE * parent2.startAdd + 4);
                for (int i = 0; i < cnt; i++) {  //获取磁盘中文件的详细信息
                    byte[] filename1 = new byte[32];
                    rw.read(filename1);
                    String name1 = new String(filename1, StandardCharsets.UTF_8).trim();
                    byte[] filetype = new byte[32];
                    rw.read(filetype);
                    String type1 = new String(filetype, StandardCharsets.UTF_8).trim();
                    int length1 = rw.readInt();
                    int startAdd1 = rw.readInt();
                    byte[] createt = new byte[16];
                    rw.read(createt);
                    String createTime1 = new String(createt, StandardCharsets.UTF_8).trim();
                    byte[] updatet = new byte[16];
                    rw.read(updatet);
                    String updateTime1 = new String(updatet, StandardCharsets.UTF_8).trim();
                    if (name1.equals(parent.name) && type1.equals("文件夹")) {
                        //在磁盘中更新更新时间
                        rw.seek(BLOCK_SIZE * parent2.startAdd + 4 + i * 104 + 64 + 8 + 16);
                        byte[] updateTime = parent.lastUpdateTime.getBytes(StandardCharsets.UTF_8);
                        updateTime = Arrays.copyOf(updateTime, 16);
                        rw.write(updateTime);
                        break;
                    }
                }
                rw.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        System.out.println("删除成功!");
    }

    public static String readFile(String instruction) {
        String[] inss = instruction.split(" ");
        if (inss.length == 1) {
            System.out.println("输入的指令有误！");
            return null;
        }
        FCB fcb = null; //要读文件的fcb
        boolean flag = false;
        if (!instruction.contains(curUser.username + ":")) { //代表在当前目录下读文件
            String filename = inss[1];
            FCB curDir = FileSystem.curDir;
            for (int i = 0; i < curDir.childFCB.size(); i++) {
                if (curDir.childFCB.get(i).name.equals(filename) && curDir.childFCB.get(i).type.equals("文件")) {
                    fcb = curDir.childFCB.get(i);
                    flag = true;
                    break;
                }
            }
        } else {  //在绝对路径下读文件 ，例： read l:\a\file.txt
            String path = inss[1].substring(0, inss[1].lastIndexOf("\\")); //获得目录路径
            String filename = inss[1].substring(inss[1].lastIndexOf("\\") + 1); //获得文件名
            FCB curDir = cd(path); //获得文件所在目录fcb
            if (curDir == null) {
                System.out.println("路径错误!");
                return null;
            }
            for (int i = 0; i < curDir.childFCB.size(); i++) {
                if (curDir.childFCB.get(i).name.equals(filename) && curDir.childFCB.get(i).type.equals("文件")) {
                    flag = true;
                    fcb = curDir.childFCB.get(i);
                    break;
                }
            }
        }
        if (!flag) {
            System.out.println("未找到该文件!");
            return null;
        }
        if (!ActiveFile.find(fcb)) {
            System.out.println("该文件未被打开!");
            return null;
        }
        //文件存储的起始地址
        int start = fcb.startAdd;
        //文件大小
        int length = fcb.length;
        if (length == 0) {
            return null;
        }
        //需要从磁盘中读的所有内容
        byte[] content = new byte[length];
        //下一个磁盘块读的内容需要拼接的位置
        int pos = 0;
        while (start != -2) {
            try {
                RandomAccessFile rw = new RandomAccessFile(DISK_PATH, "rw");
                rw.skipBytes(start * BLOCK_SIZE);
                //还需要读的字节数 length
                if (length <= BLOCK_SIZE) {
                    byte[] data = new byte[length];
                    rw.read(data);
                    System.arraycopy(data, 0, content, pos, length);
                    length = 0;
                    pos += length;
                } else {
                    byte[] data = new byte[BLOCK_SIZE];
                    rw.read(data);
                    System.arraycopy(data, 0, content, pos, BLOCK_SIZE);
                    length -= BLOCK_SIZE;
                    pos += BLOCK_SIZE;
                }
                rw.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            start = FAT.fileFAT.get(start);
        }
        String result = new String(content, StandardCharsets.UTF_8).trim();
        return result;
    }

    public static void writeFile(String instruction, String s) {
        String[] inss = instruction.split(" ");
        if (inss.length == 1) {
            System.out.println("输入的指令有误！");
            return;
        }
        FCB fcb = null; //要写文件的fcb
        boolean flag = false;
        //在当前目录下写文件
        if (!instruction.contains(curUser.username + ":")) {
            String filename = inss[1];
            FCB curDir = FileSystem.curDir;
            for (int i = 0; i < curDir.childFCB.size(); i++) {
                if (curDir.childFCB.get(i).name.equals(filename) && curDir.childFCB.get(i).type.equals("文件")) {
                    fcb = curDir.childFCB.get(i);
                    flag = true;
                    break;
                }
            }
        } else {  //在绝对路径下写文件 ，例： write li:\a\file.txt
            String path = inss[1].substring(0, inss[1].lastIndexOf("\\")); //获得目录路径
            String filename = inss[1].substring(inss[1].lastIndexOf("\\") + 1); //获得文件名
            FCB curDir = cd(path); //获得文件所在目录fcb
            if (curDir == null) {
                System.out.println("路径错误!");
                return;
            }
            for (int i = 0; i < curDir.childFCB.size(); i++) {
                if (curDir.childFCB.get(i).name.equals(filename) && curDir.childFCB.get(i).type.equals("文件")) {
                    flag = true;
                    fcb = curDir.childFCB.get(i);
                    break;
                }
            }
        }
        if (!flag) {
            System.out.println("未找到该文件!");
            return;
        }
        if (!ActiveFile.find(fcb)) {
            System.out.println("该文件未被打开!");
            return;
        }
        //找到要写的文件，下面开始获取写的内容
        String content = ""; //要写的内容
        if (s == null) {
            System.out.println("输入写入的内容(q为结束符): ");
            Scanner sc = new Scanner(System.in);
            String tmp = "";
            while (!(tmp = sc.nextLine()).equals("q")) {
                if (content.equals(""))
                    content = tmp;
                else
                    content = content + "\n" + tmp;
            }
        } else {
            content = s;
        }
        //原有内容的大小
        int preLength = fcb.length;
        // 占这块磁盘的多少
        int last = preLength % BLOCK_SIZE;
        //这个块写完了，重新开一个盘块
        if (last == 0 && preLength != 0) {
            last = BLOCK_SIZE;
        }
        int start = fcb.startAdd;
        //找到最后一块
        int lastaddr = start;
        while (start != -2) {
            lastaddr = start;
            start = FAT.fileFAT.get(start);
        }
        byte[] data = content.getBytes(StandardCharsets.UTF_8);
        int length = data.length;//后面追加内容的大小
        if ((last + length) <= BLOCK_SIZE) { //不用再申请新的磁盘块直接在后面写
            try {
                RandomAccessFile rw = new RandomAccessFile(DISK_PATH, "rw");
                rw.skipBytes(lastaddr * BLOCK_SIZE + last);
                rw.write(data);
                rw.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else { //需要申请新的磁盘块
            //先把最后一块写满
            int left = BLOCK_SIZE - last;  //最后一块磁盘块还剩多少空间
            if (left != 0) {
                byte[] difdata = new byte[left];
                System.arraycopy(data, 0, difdata, 0, left);
                try {
                    RandomAccessFile rw = new RandomAccessFile(DISK_PATH, "rw");
                    rw.skipBytes(lastaddr * BLOCK_SIZE + last);
                    rw.write(difdata);
                    rw.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            int requireSize = length - left;//还需要多少空间
            // 还需要多少块磁盘块
            int need = requireSize / BLOCK_SIZE;
            if (requireSize % BLOCK_SIZE != 0) {
                need++;
            }
            ArrayList<Integer> free = Block.findFree(need);
            if (free.size() < need) {
                System.out.println("空间已满，写入失败");
                return;
            }
            //更新FAT表
            FAT.fileFAT.set(lastaddr, free.get(0));
            for (int i = 1; i < free.size(); i++) {
                FAT.fileFAT.set(free.get(i - 1), free.get(i));
            }
            FAT.fileFAT.set(free.get(free.size() - 1), -1);
            try {
                int pos = left;//记录从data数组的哪里开始写
                RandomAccessFile rw = new RandomAccessFile(DISK_PATH, "rw");
                for (int i = 0; i < free.size(); i++) {
                    rw.seek(free.get(i) * BLOCK_SIZE);
                    if (i != free.size() - 1) { //代表不是最后一块所以需要写满BLOCK_SIZE
                        byte[] data1 = new byte[BLOCK_SIZE];
                        System.arraycopy(data, pos, data1, 0, BLOCK_SIZE);
                        pos += BLOCK_SIZE;
                        rw.write(data1);
                    } else { //表示最后一块
                        int res = length - pos + 1; //最后剩余需要写的内容
                        byte[] data1 = new byte[res];
                        System.arraycopy(data, pos, data1, 0, res);
                        rw.write(data1);
                    }
                }
                rw.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        fcb.length += length;//更新文件大小,以及更新时间
        Date date = new Date();
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        String time = formatter.format(date);
        fcb.lastUpdateTime = time;
        FCB parent = fcb.parent;
        int num = FileSystem.readInt(BLOCK_SIZE * parent.startAdd);//获取父级目录下文件个数
        try {
            RandomAccessFile rw = new RandomAccessFile(DISK_PATH, "rw");
            rw.skipBytes(BLOCK_SIZE * parent.startAdd + 4);
            for (int i = 0; i < num; i++) {  //获取磁盘中文件的详细信息
                byte[] filename = new byte[32];
                rw.read(filename);
                String name1 = new String(filename, StandardCharsets.UTF_8).trim();
                byte[] filetype = new byte[32];
                rw.read(filetype);
                String type1 = new String(filetype, StandardCharsets.UTF_8).trim();
                int length1 = rw.readInt();
                int startAdd1 = rw.readInt();
                byte[] createt = new byte[16];
                rw.read(createt);
                String createTime1 = new String(createt, StandardCharsets.UTF_8).trim();
                byte[] update = new byte[16];
                rw.read(update);
                String updateTime1 = new String(update, StandardCharsets.UTF_8).trim();
                if (name1.equals(fcb.name) && type1.equals("文件")) {
                    rw.seek(BLOCK_SIZE * parent.startAdd + 4 + i * 104L + 64);
                    rw.writeInt(fcb.length);
                    rw.seek(BLOCK_SIZE * parent.startAdd + 4 + i * 104L + 64 + 8 + 16);
                    byte[] updateTime = fcb.lastUpdateTime.getBytes(StandardCharsets.UTF_8);
                    updateTime = Arrays.copyOf(updateTime, 16);
                    rw.write(updateTime);
                    break;
                }
            }
            rw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void exportFile(String instruction) {
        String[] inss = instruction.split(" ");  //export a.txt c:\
        if (inss.length == 1 || inss.length == 2) {
            System.out.println("输入的指令有误！");
            return;
        }
        FCB fcb = openFile("open " + inss[1], false);
        if (fcb != null) {
            //把虚拟磁盘中的文件读出
            String content = "";
            content = readFile("read " + inss[1]);
            //将读出的内容写到系统本地磁盘
            try {
                File file = new File(inss[2] + "\\" + fcb.name);
                if (file.exists()) {
                    System.out.println("系统已有文件，导出失败");
                    return;
                }
                //如果内容为空
                if (content == null) {
                    file.createNewFile();
                    System.out.println("导出成功！");
                    return;
                }
                FileWriter fw = new FileWriter(new File(inss[2] + "\\" + fcb.name));
                fw.write(content);
                fw.close();
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }
        ActiveFile.close(fcb, false);
        System.out.println("导出成功!");
    }

    public static void importFile(String instruction) {
        String[] inss = instruction.split(" ");  //import c:\a.txt .
        if (inss.length == 1 || inss.length == 2) {
            System.out.println("输入的指令有误！");
            return;
        }
        File sourcefile = new File(inss[1]);
        if (!sourcefile.exists()) {
            System.out.println("源文件不存在");
        }
        String content = new String("");
        try {  //读出源文件内容
            FileReader fr = new FileReader(sourcefile);
            BufferedReader br = new BufferedReader(fr);
            String s = "";
            while ((s = br.readLine()) != null) {
                content = content + s + "\n";
            }
            br.close();
            fr.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        FCB targetDir = null;
        if (inss[2].equals(".")) { //代表复制到当前目录下
            targetDir = curDir;
        } else { //复制到指定路径下
            targetDir = cd(inss[2]);
            if (targetDir == null) {
                System.out.println("指定路径错误!");
                return;
            }
        }
        FCB newfile = targetDir.createFCB(sourcefile.getName(), "文件");
        if (newfile == null) {
            return;
        }
        ActiveFile.open(newfile, false);
        if (inss[2].equals("."))
            writeFile("write " + sourcefile.getName(), content);
        else
            writeFile("write " + inss[2] + "\\" + sourcefile.getName(), content);
        ActiveFile.close(newfile, false);
        System.out.println("导入成功!");
    }

    public static void xcopyDir(String instruction) {
        String[] inss = instruction.split(" ");
        if (inss.length == 1 || inss.length == 2) {
            System.out.println("输入的指令有误！");
            return;
        }
        if (!inss[1].contains(curUser.username + ":")) { //当前目录下的路径先变为绝对路径
            inss[1] = getPath() + "\\" + inss[1];
        }
        //当前路径下的要被复制的目录
        FCB copyfcb = cd(inss[1]);
        if (copyfcb == null) {
            System.out.println("找不到当前路径下的目录");
            return;
        }
        FCB targetDir = cd(inss[2]); //获得目标路径的目录fcb
        if (targetDir == null) {
            System.out.println("目标路径错误!");
            return;
        }
        //在目标目录创建一个新的目录
        FCB newDir = targetDir.createFCB(copyfcb.name, "文件夹");
        //复制
        copyDir(copyfcb, newDir, inss[1], inss[2]);
        System.out.println("复制成功!");
    }

    /**
     * 递归复制
     *
     * @param source    目前路径下的要被复制的源目录
     * @param targetFcb 最新创建的 a
     * @param path      当前路径下目录 a -> l:\a
     * @param target    目标路径 l:\b
     */
    public static void copyDir(FCB source, FCB newFCB, String path, String target) {
        for (int i = 0; i < source.childFCB.size(); i++) {
            FCB fcb = source.childFCB.get(i);
            if (fcb.type.equals("文件")) {
                // l:\a + \1.txt -> l:\b
                copyFile2("copy " + path + "\\" + fcb.name + " " + target + "\\" + newFCB.name);
            } else {
                //创建一个同名文件
                FCB newDir = newFCB.createFCB(fcb.name, "文件夹");
                //进入文件下一层，递归  fcb即当前目录下一层
                if (fcb.childFCB.size() != 0) {
                    copyDir(fcb, newDir, path + "\\" + fcb.name, target + "\\" + newFCB.name);
                    //path进入下一层l:\a\...
                    // target: 进入下一层l:\b\a
                }
            }
        }
    }

    public static void copyFile2(String instruction) {
        String[] ins = instruction.split(" ");
        if (ins.length == 1 || ins.length == 2) {
            System.out.println("输入的指令有误！");
            return;
        }
        FCB sourceFilefcb = openFile("open " + ins[1], false);
        if (sourceFilefcb == null) {
            return;
        }
        FCB targetDir = cd(ins[2]); //获得目标路径的目录fcb
        if (targetDir == null) {
            System.out.println("目标路径错误!");
            return;
        }
        FCB newfile = targetDir.createFCB(sourceFilefcb.name, "文件");
        if (newfile == null) {
            return;
        }
        ActiveFile.open(sourceFilefcb, false);
        ActiveFile.open(newfile, false);
        if (sourceFilefcb.length != 0) { //将原文件的内容写到复制的文件中

            String content = readFile("read " + ins[1]);
            if (content == null) {
                return;
            }
            writeFile("write " + ins[2] + "\\" + sourceFilefcb.name, content);
        }
        ActiveFile.close(sourceFilefcb, false);
        ActiveFile.close(newfile, false);
    }

    public static void copyFile(String instruction) {
        String[] ins = instruction.split(" ");  //copy file1 121:\dir1
        if (ins.length == 1 || ins.length == 2) {
            System.out.println("输入的指令有误！");
            return;
        }
        FCB sourceFilefcb = openFile("open " + ins[1], false);
        if (sourceFilefcb == null) {
            return;
        }
        FCB targetDir = cd(ins[2]); //获得目标路径的目录fcb
        if (targetDir == null) {
            System.out.println("目标路径错误!");
            return;
        }
        FCB newfile = targetDir.createFCB(sourceFilefcb.name, "文件");
        if (newfile == null) {
            return;
        }
        ActiveFile.open(sourceFilefcb, false);
        ActiveFile.open(newfile, false);
        if (sourceFilefcb.length != 0) { //将原文件的内容写到复制的文件中

            String content = readFile("read " + ins[1]);
            if (content == null) {
                return;
            }
            writeFile("write " + ins[2] + "\\" + sourceFilefcb.name, content);
        }
        ActiveFile.close(sourceFilefcb, false);
        ActiveFile.close(newfile, false);
        System.out.println("复制成功!");
    }

    public static void closeFile(String instruction) {
        String[] inss = instruction.split(" ");
        if (inss.length == 1) {
            System.out.println("输入的指令有误！");
            return;
        }
        //在当前目录下关闭文件
        if (!instruction.contains(curUser.username + ":")) {
            String filename = inss[1];
            FCB curDir = FileSystem.curDir;
            for (int i = 0; i < curDir.childFCB.size(); i++) {
                if (curDir.childFCB.get(i).name.equals(filename) && curDir.childFCB.get(i).type.equals("文件")) {
                    ActiveFile.close(curDir.childFCB.get(i), true);
                    return;
                }
            }
            System.out.println("当前目录下未找到该文件");
            //在绝对路径下关闭文件 ，例： close user1:\dir1\file.txt
        } else {
            //获得目录路径
            String path = inss[1].substring(0, inss[1].lastIndexOf("\\"));
            //获得文件名
            String filename = inss[1].substring(inss[1].lastIndexOf("\\") + 1);
            FCB curDir = cd(path); //获得目标路径文件
            if (curDir == null) {
                System.out.println("路径错误!");
                return;
            }
            for (int i = 0; i < curDir.childFCB.size(); i++) {
                if (curDir.childFCB.get(i).name.equals(filename) && curDir.childFCB.get(i).type.equals("文件")) {
                    ActiveFile.close(curDir.childFCB.get(i), true);
                    return;
                }
            }
            System.out.println("路径错误!");
        }
    }

    /**
     * @param instruction
     * @param flag
     * @return
     */
    public static FCB openFile(String instruction, boolean flag) {
        String[] inss = instruction.split(" ");
        if (inss.length == 1) {
            System.out.println("输入的指令有误！");
            return null;
        }
        if (!instruction.contains(curUser.username + ":")) { //代表在当前目录下打开文件
            String filename = inss[1];
            FCB curDir = FileSystem.curDir;
            for (int i = 0; i < curDir.childFCB.size(); i++) {
                if (curDir.childFCB.get(i).name.equals(filename) && curDir.childFCB.get(i).type.equals("文件")) {
                    ActiveFile.open(curDir.childFCB.get(i), flag);
                    return curDir.childFCB.get(i);
                }
            }
            System.out.println("在当前目录下未找到该文件");
        } else {  //在绝对路径下打开文件 ，例： instructionen l:\dir1\file.txt
            String path = inss[1].substring(0, inss[1].lastIndexOf("\\")); //获得目录路径
            String filename = inss[1].substring(inss[1].lastIndexOf("\\") + 1); //获得文件名
            FCB curDir = cd(path); //获得目录fcb
            if (curDir == null) {
                System.out.println("路径错误!");
                return null;
            }
            for (int i = 0; i < curDir.childFCB.size(); i++) {
                if (curDir.childFCB.get(i).name.equals(filename) && curDir.childFCB.get(i).type.equals("文件")) {
                    ActiveFile.open(curDir.childFCB.get(i), flag);
                    return curDir.childFCB.get(i);
                }
            }
            System.out.println("路径错误!");
        }
        return null;
    }

    public static void cdDir(String instruction) {
        String[] inss = instruction.split(" ");
        if (inss.length == 1) {
            System.out.println("输入的指令有误！");
            return;
        }
        if (inss[1].equals("..")) { //返回上级目录
            if (curDir.parent != null) {
                curDir = curDir.parent;
            }
            return;
        } else if (inss[1].equals(curUser.username + ":")) {  //切换到用户目录
            curDir = curUser.userFCB;
        } else if (!instruction.contains(curUser.username + ":")) { //进入当前目录的子目录
            ArrayList<FCB> file = curDir.childFCB; //遍历当前目录里所有文件
            for (int i = 0; i < file.size(); i++) {
                FCB fcb = file.get(i);
                if (fcb.name.equals(inss[1])) {
                    if (fcb.type.equals("文件")) {
                        System.out.println("要进入的目录为文件名!");
                        return;
                    } else {
                        curDir = fcb;
                        return;
                    }
                }
            }
            System.out.println("找不到该目录!");
            return;
        } else { //给定的绝对路径
            String[] path = inss[1].split("\\\\"); //以”\“分割给定路径
            if (!path[0].equals(curUser.username + ":")) { //最高级目录不符合则路径错误
                System.out.println("路径错误!");
                return;
            }
            FCB fcb = curUser.userFCB;
            for (int i = 1; i < path.length; i++) { //遍历给定路径的各级目录
                boolean flag = false;
                if (fcb.childFCB != null) {
                    for (int j = 0; j < fcb.childFCB.size(); j++) {
                        if (fcb.childFCB.get(j).name.equals(path[i]) && fcb.childFCB.get(j).type.equals("文件夹")) {
                            if (i == path.length - 1) { //已经遍历到最后一个目录了
                                curDir = fcb.childFCB.get(j);
                                return;
                            } else { //后面还有更下级的目录
                                fcb = fcb.childFCB.get(j);
                                flag = true;
                                break;
                            }
                        }
                    }
                }
                if (!flag) {
                    System.out.println("路径错误!");
                    return;
                }
            }
            System.out.println("路径错误!");
        }
    }

    //返回绝对路径下的目录fcb
    public static FCB cd(String path) {
        if (path.equals(curUser.username + ":")) {  //绝对路径为用户目录
            return curUser.userFCB;
        }
        String[] path1 = path.split("\\\\");
        FCB fcb = curUser.userFCB;
        for (int i = 1; i < path1.length; i++) { //遍历给定路径的各级目录
            boolean flag = false;
            if (fcb.childFCB != null) {
                for (int j = 0; j < fcb.childFCB.size(); j++) {
                    if (fcb.childFCB.get(j).name.equals(path1[i]) && fcb.childFCB.get(j).type.equals("文件夹")) {
                        if (i == path1.length - 1) { //已经遍历到最后一个目录了
                            return fcb.childFCB.get(j);
                        } else { //后面还有更下级的目录
                            fcb = fcb.childFCB.get(j);
                            flag = true;
                            break;
                        }
                    }
                }
            }
            if (!flag) {
                return null;
            }
        }
        return null;
    }

    public static void loginPage() {
        System.out.println("----------欢迎进入文件管理系统----------");
        System.out.println("\t\t1:登录\t\t");
        System.out.println("\t\t2:注册\t\t");
        System.out.println("\t\t3:退出系统\t\t");
        Scanner sc = new Scanner(System.in);
        while (true) {
            System.out.print("请输入你的选择:");
            String choice = sc.next();
            if (choice.equals("1")) {
                System.out.print("请输入用户名:");
                String username = sc.next();
                System.out.print("请输入密码:");
                String password = sc.next();
                if (login(username, password)) {
                    break;
                }
            } else if (choice.equals("2")) {
                System.out.print("请输入用户名:");
                String username = sc.next();
                System.out.print("请输入密码:");
                String password = sc.next();
                if (register(username, password)) {
                    System.out.println("注册成功");
                }
            } else {
                System.exit(0);
            }
        }
    }

    public static void help() {
        System.out.println("help\t" + "显示所有操作介绍");
        System.out.println("dir [path/dirname]\t" + "列给定路径或当前目录下的文件目录");
        System.out.println("treedir [path/dirname]\t" + "循环列出给定路径或当前目录下的子子孙孙目录和文件形式，并以树形显示");
        System.out.println("mkdir [dirname]\t" + "在当前目录下创建给定名字的目录");
        System.out.println("deldir [path/dirname]\t" + "删除给定路径或当前目录下的空目录，若不为空则提醒");
        System.out.println("xcopydir [path/dirname] [path]\t" + "给定给定路径或当前目录下的某个目录名，将它连同其子子孙孙复制到给定的路径下");
        System.out.println("cd [path/dirname/..]\t" + "切换给定路径或当前目录下的文件目录为当前目录，若为文件名则提醒出错");
        System.out.println("create [path/filename]\t" + "创建指定路径或当前目录下给定文件名的文件");
        System.out.println("open [path/filename]\t" + "打开指定路径或当前目录下给定文件名的文件，供下面的read/write操作调用");
        System.out.println("close [path/filename]\t" + "关闭指定路径或当前目录下给定文件名的文件，供下面的read/write操作调用");
        System.out.println("read [path/filename]\t" + "读指定路径或当前目录下给定文件名的文件，支持通过光标详细浏览文件");
        System.out.println("write [path/filename]\t" + "写指定路径或当前目录下给定文件名的文件，追加模式下写入文件内容");
        System.out.println("copy [path/filename] [path]\t" + "将指定路径或当前目录下给定文件名的文件复制到给定路径下");
        System.out.println("import [path] [path/dirname/.]\t" + "将本地磁盘下的文件导入到虚拟磁盘中。");
        System.out.println("export [path/filename] [path]\t" + "从虚拟磁盘驱动器复制内容到本地磁盘。");
        System.out.println("quit\t" + "退出系统");
    }

    public static void writeInt(int num, int pos) {
        try {
            RandomAccessFile rw = new RandomAccessFile(DISK_PATH, "rw");
            rw.skipBytes(pos);
            rw.writeInt(num);
            rw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static int readInt(int pos) {
        try {
            RandomAccessFile rw = new RandomAccessFile(DISK_PATH, "rw");
            rw.skipBytes(pos);
            int readInt = rw.readInt();
            rw.close();
            return readInt;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return -1;
    }
}
