package megvii.testfacepass.custom.importmanager;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;


/**
 * 导入相关管理类
 * Created by v_liujialu01 on 2019/5/28.
 */

public class ImportFileManager {
    private static final String TAG = "ImportFileManager";

    private Future mFuture;
    private ExecutorService mExecutorService;
    private OnImportListener mImportListener;
    // 是否需要导入
    private volatile boolean mIsNeedImport;

    private int mTotalCount;
    private int mFinishCount;
    private int mSuccessCount;
    private int mFailCount;

    private final String GROUP_NAME = "facepass";
    private final String DELIMITER = "-";

    private static class HolderClass {
        private static final ImportFileManager instance = new ImportFileManager();
    }

    public static ImportFileManager getInstance() {
        return ImportFileManager.HolderClass.instance;
    }

    // 私有构造，实例化ExecutorService
    private ImportFileManager() {
        if (mExecutorService == null) {
            mExecutorService = Executors.newSingleThreadExecutor();
        }
        LogUtils.setIsDebug(true);
    }

    public void setOnImportListener(OnImportListener importListener) {
        mImportListener = importListener;
    }

    /**
     * 开始批量导入
     */
    public void batchImport() {
        // 获取导入目录 /sdcard/Face-Import
        File batchFaceDir = FileUtils.getBatchImportDirectory();
        LogUtils.i(TAG, "batchFaceDir:" + batchFaceDir);
        // 遍历该目录下的所有文件
        String[] files = batchFaceDir.list();
        if (files == null || files.length == 0) {
            LogUtils.i(TAG, "导入数据的文件夹没有数据");
            if (mImportListener != null) {
                mImportListener.showToastMessage("导入数据的文件夹没有数据");
            }
            return;
        }

        // 判断Face.zip是否存在
        File zipFile = FileUtils.isFileExist(batchFaceDir.getPath(), "Face.zip");
        if (zipFile == null) {
            LogUtils.i(TAG, "导入数据的文件夹没有Face.zip");
            if (mImportListener != null) {
                mImportListener.showToastMessage("搜索失败，请检查操作步骤并重试");
            }
            return;
        }

        // 开启线程解压、导入
        asyncImport(batchFaceDir, zipFile);
    }

    /**
     * 开启线程解压、导入
     *
     * @param batchFaceDir 导入的目录
     * @param zipFile      压缩文件
     */
    private void asyncImport(final File batchFaceDir, final File zipFile) {
        mIsNeedImport = true;
        mFinishCount = 0;
        mSuccessCount = 0;
        mFailCount = 0;

        if (mExecutorService == null) {
            mExecutorService = Executors.newSingleThreadExecutor();
        }

        mFuture = mExecutorService.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    if (mImportListener != null) {
                        mImportListener.startUnzip();
                    }

                    // 解压
                    boolean zipSuccess = ZipUtils.unZipFolder(zipFile.getAbsolutePath(), batchFaceDir.toString());
                    if (!zipSuccess) {
                        if (mImportListener != null) {
                            mImportListener.showToastMessage("解压失败");
                        }
                        return;
                    }

                    // 删除zip文件
                    FileUtils.deleteFile(zipFile.getPath());
                    LogUtils.i(TAG, "解压成功");

                    // 解压成功之后再次遍历该目录是不是存在文件
                    File batchPicDir = FileUtils.getBatchImportDirectory();
                    LogUtils.i(TAG, "dir:" + batchPicDir);
                    File[] files = batchPicDir.listFiles();

                    // 如果该目录下没有文件，则提示获取图片失败
                    if (files == null) {
                        if (mImportListener != null) {
                            mImportListener.showToastMessage("获取图片失败");
                        }
                        return;
                    }
                } catch (Exception e) {
                    LogUtils.e(TAG, "exception = " + e.getMessage());
                    e.printStackTrace();
                }
            }
        });
    }

    private void updateProgress(int finishCount, int successCount, int failureCount, float progress) {
        if (mImportListener != null) {
            mImportListener.onImporting(finishCount, successCount, failureCount, progress);
        }
    }

    /**
     * 释放功能，用于关闭线程操作
     */
    public void release() {
        if (mFuture != null && !mFuture.isCancelled()) {
            mFuture.cancel(true);
            mFuture = null;
        }

        if (mExecutorService != null) {
            mExecutorService = null;
        }
    }


}
