package test;

import cz.zcu.kiv.WorkflowDesigner.Annotations.BlockExecute;
import cz.zcu.kiv.WorkflowDesigner.Annotations.BlockInput;
import cz.zcu.kiv.WorkflowDesigner.Annotations.BlockOutput;
import cz.zcu.kiv.WorkflowDesigner.Annotations.BlockType;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import static cz.zcu.kiv.WorkflowDesigner.Type.FILE;
import static cz.zcu.kiv.WorkflowDesigner.Type.STREAM;


@BlockType(type ="StreamToFile", family = "STREAM")
public class StreamToFile {



    @BlockInput(name = "Input", type = "STREAM")
    private InputStream in;

    @BlockOutput(name = "Output", type ="FILE")
    private File outputFile;

    @BlockExecute
    public void process(){

        outputFile = new File("/Users/yijie/Desktop/INCF/stream.txt");

        try{
            OutputStream out = new FileOutputStream(outputFile);
            byte[] bytes= new byte[1024];

            while(in.read(bytes)!=-1){

                out.write(bytes);
                out.flush();
            }
            out.close();
            in.close();

        } catch (Exception e){
            e.printStackTrace();
        }


    }








}
