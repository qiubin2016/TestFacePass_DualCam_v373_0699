package megvii.testfacepass.custom.importmanager;

/**
 * 导入相关listener
 * Created by liujialu on 2019/6/3.
 */

public interface OnImportListener {

    void startUnzip();

    void showProgressView();

    void onImporting(int finishCount, int successCount, int failureCount, float progress);

    void onImporting(int finishCount, int successCount, int failureCount, int totalCount, float progress);

    void endImport(int finishCount, int successCount, int failureCount);

    void endImport(int finishCount, int successCount, int failureCount, int totalCount);

    void showToastMessage(String message);
}
