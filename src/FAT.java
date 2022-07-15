
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;

/**
 * 此类为FAT表工具类
 * 前4块存FAT表
 */
public class FAT {
    //存储FAT的集合
    public static ArrayList<Integer> fileFAT = new ArrayList<>();

    //将新的FAT表写入磁盘，用于更新磁盘
    public static void writeFAT() {
        try {
            RandomAccessFile rw = new RandomAccessFile(FileSystem.DISK_PATH, "rw");
            for (int i = 0; i < FileSystem.BLOCK_SIZE; i++) {
                rw.writeInt(fileFAT.get(i));
            }
            rw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //将磁盘的FAT表读出来
    public static void readFAT() {
        //先清空原先的fat再更新
        fileFAT.clear();
        try {
            RandomAccessFile rw = new RandomAccessFile(FileSystem.DISK_PATH, "rw");
            for (int i = 0; i < FileSystem.BLOCK_SIZE; i++) {
                fileFAT.add(rw.readInt());
            }
            rw.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


}
