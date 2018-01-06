package com.creat.huffman;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;

/**
 * 哈夫曼压缩
 * Created by whz on 2018-01-02.
 */
public class HuffmanReduce {
    private Map<Byte,Integer> countMap;
    private String[] huffmanCode;
    private Queue<Node> queue;
    private String path;

    public static void main(String[] args) {
        HuffmanReduce huffmanReduce = new HuffmanReduce();
        //huffmanReduce.compress("E:\\a.txt");
        huffmanReduce.decompress("E:\\a.huf","E:\\aa.txt");
    }
    //8位字符串转字节
    public static byte bitToByte(String byteStr) throws HuffmanException {
        int result, len;
        if (byteStr == null) {
            throw new HuffmanException("不能为空!");
        }
        len = byteStr.length();
        if (len != 8) {
            throw new HuffmanException("必须为8位!");
        }
        if (byteStr.charAt(0) == '0') {// 正数
            result = Integer.parseInt(byteStr, 2);
        } else {// 负数
            result = Integer.parseInt(byteStr, 2) - 256;
        }
        return (byte) result;
    }
    //字节转8位字符串
    public static String byteToBit(byte b) {
        return ""
                + (byte) ((b >> 7) & 0x1) + (byte) ((b >> 6) & 0x1)
                + (byte) ((b >> 5) & 0x1) + (byte) ((b >> 4) & 0x1)
                + (byte) ((b >> 3) & 0x1) + (byte) ((b >> 2) & 0x1)
                + (byte) ((b >> 1) & 0x1) + (byte) ((b >> 0) & 0x1);
    }
    //压缩初始化
    public void init(){
        countMap = new HashMap<Byte, Integer>();
        huffmanCode = new String[256];//偏移量为128
        queue = new PriorityQueue<Node>();
    }

    //压缩
    public void compress(String path){
        this.path = path;
        try {
            init();
            buildCountMap();
            buildQueue();
            buildTree();
            buildHuffmanCode();
            produce(1024*10);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (HuffmanException e) {
            e.printStackTrace();
        }
    }

    //解压缩
    public void decompress(String path, String newPath){
        this.path = path;
        init();
        try {
            BufferedInputStream inputStream = new BufferedInputStream(new FileInputStream(path));
            getCountMap(inputStream);
            buildQueue();
            buildTree();
            startDecompress(inputStream,newPath);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public void startDecompress(BufferedInputStream inputStream, String newPath) throws IOException {
        BufferedOutputStream outputStream = new BufferedOutputStream(new FileOutputStream(newPath));
        byte[] bytes = new byte[inputStream.available()];
        inputStream.read(bytes);
        byte lackNum = bytes[bytes.length - 1];
        String writeStr = new String();
        Node root = queue.poll();
        for(int i = 0; i < bytes.length - 1; i++){
            String temp = byteToBit(bytes[i]);
            if(lackNum > 0 && i == bytes.length -2){
                temp  = temp.substring(0, 8-lackNum);
            }
            writeStr += temp;
            if(i % 4 == 0 || i == bytes.length - 2){
                while(writeStr.charAt(0) != '2'){
                    writeStr = write(outputStream, root, writeStr, "");
                }
                if(writeStr.length() == 1){
                    writeStr = "";
                }else {
                    writeStr = writeStr.substring(1);
                }
            }
        }
        outputStream.flush();
        outputStream.close();
        inputStream.close();
    }

    public String write(OutputStream outputStream,Node node, String str,String byteStr) throws IOException {
        if(!node.isLeaf()){
            if(str.charAt(0) == '2'){
                return "2" + byteStr;
            }else if(str.charAt(0) == '0'){
                return write(outputStream, node.left, str.length() > 1 ? str.substring(1) : "2",byteStr + "0");
            }else {
                return write(outputStream, node.right, str.length() > 1 ? str.substring(1) : "2", byteStr + "1");
            }
        }else {
            outputStream.write(new byte[]{node.b});
            return str;
        }
    }

    //从压缩文件中获得词频
    public void getCountMap(InputStream inputStream) throws IOException {
       int count = inputStream.read();
       for(int i = 0; i < count; i++){
           byte[] bytes = new byte[1];
           inputStream.read(bytes);
           int cipin = inputStream.read();
           countMap.put(bytes[0], cipin);
       }
    }

    //开始压缩
    public void produce(int bufSize) throws IOException, HuffmanException {
        String newPath = getNewPath();
        BufferedInputStream inputStream = new BufferedInputStream(new FileInputStream(path));
        BufferedOutputStream outputStream = new BufferedOutputStream(new FileOutputStream(newPath));
        outputStream.write(countMap.size());//写入字节的种类数量
        for(byte b : countMap.keySet()){//写入字节+频率
            outputStream.write(new byte[]{b},0,1);
            outputStream.write(countMap.get(b));
        }
        byte[] inBuf = new byte[bufSize];
        int inBufLength = 0;
        String writeStr = "";
        byte[] outBuf = new byte[bufSize];
        int outBufLength = 0;
        while ((inBufLength = inputStream.read(inBuf)) != -1){
            for(int i = 0; i < inBufLength; i++){
                byte bIn = inBuf[i];
                writeStr += huffmanCode[bIn + 128];
            }
            while(writeStr.length() >= 8){
                outBuf[outBufLength++] = bitToByte(writeStr.substring(0,8));
                writeStr = writeStr.substring(8);
                if(outBufLength == bufSize){
                    outputStream.write(outBuf, 0, outBufLength);
                    outBufLength = 0;
                }
            }
            outputStream.write(outBuf, 0, outBufLength);
            outBufLength = 0;
        }
        if(writeStr.length() > 0){//填充字节
            int lackNum = 8 - writeStr.length();
            writeStr += getLackString(lackNum);
            byte last = bitToByte(writeStr);
            outputStream.write(new byte[]{last, (byte) lackNum}, 0, 2);
        }else {//最后一个字节表示填充字符的个数
            outputStream.write(new byte[]{0},0,1);
        }
        outputStream.flush();
        outputStream.close();
        inputStream.close();
    }
    //获得填充字符串
    private String getLackString(int lackNum){
        StringBuilder sb = new StringBuilder("");
        for(int i = 0; i < lackNum; i++){
            sb.append("0");
        }
        return sb.toString();
    }
    //生成新路径
    private String getNewPath(){
        int index = path.lastIndexOf(".");
        return path.substring(0, index+1)+"huf";
    }
    //遍历节点生成哈夫曼编码
    public void buildHuffmanCode() throws HuffmanException {
        if(queue.size() != 1){
            throw new HuffmanException("构建失败!");
        }else {
            Node root = queue.poll();
            traverseNode(root, "");
        }
    }
    private void traverseNode(Node node,String code){

        if(!node.isLeaf()){
            traverseNode(node.left, code + "0");
            traverseNode(node.right, code +"1");
        }else {
            huffmanCode[node.b + 128] = code;
        }
    }
    //构建优先队列
    public void buildQueue(){
        for (Byte b : countMap.keySet()){
            Node node = new Node(b, countMap.get(b), null, null);
            queue.add(node);
        }
    }
    //构建哈夫曼树
    public void buildTree(){
        while(queue.size() > 1){
           Node one = queue.poll();
           Node two = queue.poll();
           Node combineNode = new Node(null,one.weight+two.weight, one, two);
           queue.add(combineNode);
        }
    }
    //计算频率
    public void buildCountMap() throws IOException {
        BufferedInputStream inputStream = new BufferedInputStream(new FileInputStream(this.path));
        byte[] buf = new byte[256];
        int length = 0;
        while((length = inputStream.read(buf)) != -1){
            for(int i = 0; i < length; i++){
                byte b = buf[i];
                if(countMap.containsKey(b)){
                    countMap.put(b, countMap.get(b) +1 );
                }else {
                    countMap.put(b, 1);
                }
            }
        }
        inputStream.close();
    }

    class Node implements Comparable<Node>{
        Byte b;
        int weight;
        Node left;
        Node right;

        Node(Byte b, int weight, Node left, Node right){
            this.b = b;
            this.weight = weight;
            this.left = left;
            this.right = right;
        }

        boolean isLeaf(){
            if(this.right == null && this.left == null){
                return true;
            }
            return false;
        }

        public int compareTo(Node o) {
            return (this.weight < o.weight) ? -1 : ((this.weight == o.weight) ? 0 : 1);
        }

    }

}
