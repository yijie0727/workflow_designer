package test;

import cz.zcu.kiv.WorkflowDesigner.Annotations.BlockExecute;
import cz.zcu.kiv.WorkflowDesigner.Annotations.BlockOutput;
import cz.zcu.kiv.WorkflowDesigner.Annotations.BlockProperty;
import cz.zcu.kiv.WorkflowDesigner.Annotations.BlockType;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

import static cz.zcu.kiv.WorkflowDesigner.Type.STREAM;


@BlockType(type ="FileToStream", family = "STREAM")
public class FileToStream {

    @BlockProperty(name = "File", type = "FILE")
    private File fileInput;

    @BlockOutput(name = "FileStream", type = STREAM)
    private InputStream output;

    @BlockExecute
    public void process(){


        try{

            output = new FileInputStream(fileInput);


        } catch (Exception e){
            e.printStackTrace();
        }

    }



}
