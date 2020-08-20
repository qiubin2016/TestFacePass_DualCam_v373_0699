package megvii.testfacepass.custom.httpserver;

public class ImageData {
    private int id;
    private String type, image;
    private byte[] byteArr;


    public ImageData(int id, String type, String image, byte[] byteArr) {
        this.id = id;
        this.type = type;
        this.image = image;
        this.byteArr = byteArr;
    }

    public int getId() {return id; }
    public String getType() {
        return type;
    }
    public String getImage() {
        return image;
    }
    public byte[] getByteArr() { return byteArr;}
    public void setByteArr(byte[] byteArr){this.byteArr = byteArr;}
}
