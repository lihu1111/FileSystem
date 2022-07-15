import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

/**
 * 全部用户存放在第5好块
 */
public class User {
    //用户名
    String username;
    //用户密码
    String password;
    //该用户目录下所有文件的总大小
    int length;
    //用户FCB的起始地址
    int startAdd;
    //该用户的FCB记录，文件控制块记录，记录该用户下面的一级文件以及文件夹的信息
    FCB userFCB;

    public User(String username, String password, int length, int startAdd) {
        //初始化该用户的FCB表
        userFCB = new FCB();
        Date date = new Date();
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        String time = formatter.format(date);
        userFCB.name = username;
        userFCB.type = "文件夹";
        userFCB.length = length;
        userFCB.startAdd = startAdd;
        userFCB.parent = null;
        userFCB.createTime = time;
        userFCB.lastUpdateTime = time;
        userFCB.childFCB = new ArrayList<>();

        this.username = username;
        this.password = password;
        this.length = length;
        this.startAdd = startAdd;
    }

    //从磁盘中读取该用户目录下的文件或文件夹
    public void init() {
        int num = FileSystem.readInt(FileSystem.BLOCK_SIZE * startAdd);//获取该用户下文件个数
        try {
            RandomAccessFile rw = new RandomAccessFile(FileSystem.DISK_PATH, "rw");
            rw.skipBytes(FileSystem.BLOCK_SIZE * startAdd + 4);
            for (int i = 0; i < num; i++) {  //获取磁盘中文件的详细信息
                byte[] fileName = new byte[32];
                rw.read(fileName);
                String name = new String(fileName, StandardCharsets.UTF_8).trim();
                byte[] filetype = new byte[32];
                rw.read(filetype);
                String type = new String(filetype, StandardCharsets.UTF_8).trim();
                int size = rw.readInt();
                int startAdd = rw.readInt();
                byte[] createBytes = new byte[16];
                rw.read(createBytes);
                String createTime = new String(createBytes, StandardCharsets.UTF_8).trim();
                byte[] updateBytes = new byte[16];
                rw.read(updateBytes);
                String updateTime = new String(updateBytes, StandardCharsets.UTF_8).trim();
                FCB fcb = new FCB(name, type, size, startAdd, this.userFCB, createTime, updateTime);
                this.userFCB.childFCB.add(fcb);
            }
            rw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
