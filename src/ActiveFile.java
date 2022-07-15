import java.util.ArrayList;

/**
 * 此类为打开文件类
 */
public class ActiveFile {

    //存储打开文件
    public static ArrayList<FCB> fileList = new ArrayList<>();

    /**
     * 打开文件
     * @param fcb 要打开的文件
     * @param flag 是否要输出语句的标志
     */
    static void open(FCB fcb, boolean flag) {
        for (int i = 0; i < fileList.size(); i++) {
            if (fileList.get(i).startAdd == fcb.startAdd) {
                if (flag)
                    System.out.println("文件" + fcb.name + "已被打开!");
                return;
            }
        }
        fileList.add(fcb);
        if (flag)
            System.out.println("文件" + fcb.name + "打开成功!");
    }

    static void close(FCB fcb, boolean flag) {
        for (int i = 0; i < fileList.size(); i++) {
            if (fileList.get(i).startAdd == fcb.startAdd) {
                fileList.remove(i);
                if (flag)
                    System.out.println("文件" + fcb.name + "已关闭!");
                return;
            }
        }
        if (flag)
            System.out.println("文件" + fcb.name + "未被打开!");
    }

    static boolean find(FCB fcb) {
        for (FCB value : fileList) {
            if (value.startAdd == fcb.startAdd) {
                return true;
            }
        }
        return false;
    }
}
