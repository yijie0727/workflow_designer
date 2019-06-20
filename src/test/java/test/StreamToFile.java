package test;

import cz.zcu.kiv.WorkflowDesigner.Annotations.BlockExecute;
import cz.zcu.kiv.WorkflowDesigner.Annotations.BlockInput;
import cz.zcu.kiv.WorkflowDesigner.Annotations.BlockOutput;
import cz.zcu.kiv.WorkflowDesigner.Annotations.BlockType;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;


@BlockType(type ="StreamToFile", family = "STREAM")
public class StreamToFile {



    @BlockInput(name = "InputStream", type = "STREAM")
    private InputStream in;

    @BlockOutput(name = "OutputFile", type ="FILE")
    private File outputFile;

    @BlockExecute
    public File process(){

        outputFile = new File("test_data/streamTest.txt");

        try{
            OutputStream out = new FileOutputStream(outputFile);
            byte[] bytes= new byte[43];

            while(in.read(bytes)!=-1){

                out.write(bytes);
                out.flush();
            }

            String s = "\nThis is the Test of Output of StreamToFile.";
            out.write(s.getBytes());
            out.flush();

            out.close();
            in.close();

        } catch (Exception e){
            e.printStackTrace();
        }

        return outputFile;

    }








}
