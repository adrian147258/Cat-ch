package com.meowclub.app;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class UploadBodyChecks {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    interface Job { void run() throws Exception; }
    static void rejects(Job job) throws Exception {try{job.run();throw new AssertionError("Expected failure");}catch(IOException expected){}}
    public static void main(String[] args) throws Exception {
        Path dir=Files.createTempDirectory("catch-upload-checks");
        File first=dir.resolve("a.jpg").toFile(),second=dir.resolve("b.jpg").toFile();
        try {
            Files.write(first.toPath(),new byte[40000]);Files.write(second.toPath(),new byte[17000]);
            UploadBody body=new UploadBody(Arrays.asList(first.getPath(),second.getPath()),"test-boundary");
            ByteArrayOutputStream output=new ByteArrayOutputStream();ArrayList<Integer> progress=new ArrayList<>();body.write(output,progress::add);
            check(output.size()==body.length,"Content-Length must equal the complete multipart body");
            check(progress.get(progress.size()-1)==100,"Progress must finish at 100");
            for(int i=1;i<progress.size();i++)check(progress.get(i)>=progress.get(i-1),"Progress must be monotonic");
            rejects(()->new UploadBody(Collections.emptyList(),"test"));
            rejects(()->new UploadBody(Collections.nCopies(10,first.getPath()),"test"));
            Files.write(first.toPath(),new byte[1]);rejects(()->body.write(new ByteArrayOutputStream(),p->{}));
            try(RandomAccessFile file=new RandomAccessFile(first,"rw")){file.setLength(ImageFiles.MAX_UPLOAD+1);}
            rejects(()->new UploadBody(Collections.singletonList(first.getPath()),"test"));
            ByteArrayOutputStream bounded=new ByteArrayOutputStream();rejects(()->ImageFiles.copy(new ByteArrayInputStream(new byte[101]),bounded,100));check(bounded.size()<=100,"Bounded copy must not write excess bytes");
            rejects(()->ImageFiles.copy(new ByteArrayInputStream(new byte[0]),new ByteArrayOutputStream(),100));
            check(ImageFiles.sampleSize(8000,6000,1920)==8,"Large originals must be sampled before allocation");
            check(ImageFiles.key("a").length()==64&&!ImageFiles.key("a").equals(ImageFiles.key("b")),"Cache keys must be stable and distinct");
            Thread.currentThread().interrupt();rejects(()->ImageFiles.copy(new ByteArrayInputStream(new byte[10]),new ByteArrayOutputStream(),100));Thread.interrupted();
            System.out.println("PASS: multipart length, progress, count/size limits, changed files, bounded copy, sampling, cancellation");
        } finally {first.delete();second.delete();Files.deleteIfExists(dir);}
    }
}
