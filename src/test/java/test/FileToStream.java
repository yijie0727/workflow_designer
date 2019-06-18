package test;

import cz.zcu.kiv.WorkflowDesigner.Annotations.BlockExecute;
import cz.zcu.kiv.WorkflowDesigner.Annotations.BlockOutput;
import cz.zcu.kiv.WorkflowDesigner.Annotations.BlockProperty;
import cz.zcu.kiv.WorkflowDesigner.Annotations.BlockType;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;


@BlockType(type ="FileToStream", family = "STREAM")
public class FileToStream {

    @BlockProperty(name = "File", type = "FILE")
    private File fileInput;

    @BlockOutput(name = "Output", type = "STREAM")
    private InputStream output;

    @BlockExecute
    public void process(){

        if(fileInput == null) {
            System.out.println("fileInput == null");
            return;
        } else {
            System.out.println("fileInput name = "+ fileInput.getAbsolutePath());
        }

        try{

            output = new FileInputStream(fileInput);

            if(output == null)
                System.out.println("FileToStream output == null");
            else
                System.out.println("FileToStream output != null");


        } catch (Exception e){
            e.printStackTrace();
        }

    }



}
