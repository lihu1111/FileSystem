import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
public class FCB {
    //文件名
    String name;//32B
    //文件类型 文件或文件夹
    String type;
    //文件起始地址
    int startAdd;//4B
    //文件大小
    int length;//4B
    //文件创建时间
    String createTime; //16B
    //文件更新时间
    String lastUpdateTime;//16B
    //父目录
    FCB parent;
    //该文件夹存储的下一级文件
    ArrayList<FCB> childFCB = null;

    public FCB() {
    }

    public FCB(String name, String type, int length, int startAdd, FCB parent, String createTime, String lastUpdateTime) {
        this.name = name;
        this.type = type;
        this.startAdd = startAdd;
        this.length = length;
        this.parent = parent;
        this.createTime = createTime;
        this.lastUpdateTime = lastUpdateTime;
        if (type.equals("文件夹")) { //如果是文件夹
            childFCB = new ArrayList<>();
            int num = FileSystem.readInt(FileSystem.BLOCK_SIZE * startAdd);//获取该文件夹下文件个数
            try {
                RandomAccessFile rw = new RandomAccessFile(FileSystem.DISK_PATH, "rw");
                rw.skipBytes(FileSystem.BLOCK_SIZE * startAdd + 4);
                for (int i = 0; i < num; i++) {  //获取磁盘中文件的详细信息
                    byte[] filename = new byte[32];
                    rw.read(filename);
                    String name1 = new String(filename, StandardCharsets.UTF_8).trim();
                    byte[] filetype = new byte[32];
                    rw.read(filetype);
                    String type1 = new String(filetype, StandardCharsets.UTF_8).trim();
                    int size1 = rw.readInt();
                    int startAdd1 = rw.readInt();
                    byte[] createt = new byte[16];
                    rw.read(createt);
                    String createTime1 = new String(createt, StandardCharsets.UTF_8).trim();
                    byte[] updatet = new byte[16];
                    rw.read(updatet);
                    String lastUpdateTime1 = new String(updatet, StandardCharsets.UTF_8).trim();

                    FCB fcb = new FCB(name1, type1, size1, startAdd1, this, createTime1, lastUpdateTime1);
                    childFCB.add(fcb);
                }
                rw.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else
            this.childFCB = null;
    }

    /**
     * 向目标路径创建文件，并返回
     * @param filename 文件名
     * @param type 要创建的文件类型
     * @return 创建的FCB
     */
    public FCB createFCB(String filename, String type) {
        int num = FileSystem.readInt(FileSystem.BLOCK_SIZE * startAdd);//获取当前目录下文件个数
        if (num == 75) { //每个文件夹下面最多只能有78个文件
            System.out.println("文件个数已达上限");
            return null;
        }
        byte[] name = filename.getBytes(StandardCharsets.UTF_8);
        if (name.length > 32) {
            if (type.equals("文件"))
                System.out.println("文件名过长!");
            else
                System.out.println("目录名过长!");
            return null;
        }
        FCB fcb = new FCB();
        if (this.childFCB == null) this.childFCB = new ArrayList<>();
        for (FCB value : this.childFCB) {
            if (value.name.equals(filename) && value.type.equals(type)) {
                if (type.equals("文件"))
                    System.out.println("存在同名文件!");
                else
                    System.out.println("存在同名文件夹!");
                return null;
            }
        }
        //创建一个文件夹
        if (type.equals("文件夹")) {
            //为新创建的文件夹分配一块空间存放FCB
            ArrayList<Integer> free = Block.findFree(1);
            if (free.size() == 0) {
                System.out.println("磁盘空间已满");
                return null;
            }
            fcb.name = filename;
            fcb.type = type;
            fcb.startAdd = free.get(0);
            fcb.parent = this;
            fcb.childFCB = new ArrayList<>();
            fcb.length = 0;
            Date date = new Date();
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm");
            String time = formatter.format(date);
            fcb.createTime = time;
            fcb.lastUpdateTime = time;
            this.childFCB.add(fcb);
        } else { //创建一个文件
            // 为新创建的文件分配一块空间存放内容
            ArrayList<Integer> free = Block.findFree(1);
            if (free.size() == 0) {
                System.out.println("磁盘空间已满");
                return null;
            }
            fcb.name = filename;
            fcb.type = type;
            fcb.startAdd = free.get(0);
            fcb.parent = this;
            fcb.childFCB = null;
            fcb.length = 0;
            Date date = new Date();
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm");
            String time = formatter.format(date);
            fcb.createTime = time;
            fcb.lastUpdateTime = time;
            this.childFCB.add(fcb);
        }
        //在当前目录下创建文件或文件夹成功后，需要更改当前目录fcb的更新时间
        Date date = new Date();
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        String time = formatter.format(date);
        this.lastUpdateTime = time;
        if (parent != null) {
            int cnt = FileSystem.readInt(FileSystem.BLOCK_SIZE * parent.startAdd);//获取父级目录下文件个数
            try {
                RandomAccessFile rw = new RandomAccessFile(FileSystem.DISK_PATH, "rw");
                //文件指针跳到父文件夹
                rw.skipBytes(FileSystem.BLOCK_SIZE * parent.startAdd + 4);
                for (int i = 0; i < cnt; i++) {  //获取磁盘中文件的详细信息
                    byte[] filename1 = new byte[32];
                    rw.read(filename1);
                    String name1 = new String(filename1, StandardCharsets.UTF_8).trim();
                    byte[] filetype = new byte[32];
                    rw.read(filetype);
                    String type1 = new String(filetype, StandardCharsets.UTF_8).trim();
                    rw.readInt();
                    rw.readInt();
                    byte[] createtime = new byte[16];
                    rw.read(createtime);
                    String createTime1 = new String(createtime, StandardCharsets.UTF_8).trim();
                    byte[] updatetime = new byte[16];
                    rw.read(updatetime);
                    String lastUpdateTime1 = new String(updatetime, StandardCharsets.UTF_8).trim();
                    if (name1.equals(this.name) && type1.equals("文件夹")) {
                        //在磁盘中更新更新时间
                        rw.seek(FileSystem.BLOCK_SIZE * parent.startAdd + 4 + i * 104 + 64 + 8 + 16);
                        byte[] lastUpdateTime = this.lastUpdateTime.getBytes(StandardCharsets.UTF_8);
                        lastUpdateTime = Arrays.copyOf(lastUpdateTime, 16);
                        rw.write(lastUpdateTime);
                        break;
                    }
                }
                rw.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        //下面将更新后的信息写入磁盘   找到父文件夹空闲区
        try {
            RandomAccessFile rw = new RandomAccessFile(FileSystem.DISK_PATH, "rw");
            rw.skipBytes(FileSystem.BLOCK_SIZE * this.startAdd + 4 + num * 104);
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
            byte[] lastUpdateTime = fcb.lastUpdateTime.getBytes(StandardCharsets.UTF_8);
            lastUpdateTime = Arrays.copyOf(lastUpdateTime, 16);
            rw.write(lastUpdateTime);
            FileSystem.writeInt(num + 1, FileSystem.BLOCK_SIZE * this.startAdd);//更改后的文件个数写入磁盘
            rw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return fcb;
    }

}
