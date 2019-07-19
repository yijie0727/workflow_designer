package test;

import cz.zcu.kiv.WorkflowDesigner.Annotations.BlockExecute;
import cz.zcu.kiv.WorkflowDesigner.Annotations.BlockOutput;
import cz.zcu.kiv.WorkflowDesigner.Annotations.BlockType;

import java.io.ObjectOutputStream;
import java.io.PipedOutputStream;

import static cz.zcu.kiv.WorkflowDesigner.Type.STREAM;

@BlockType(type ="DataProvider1", family = "PIPE")
public class DataProvider1 {

    @BlockOutput(name = "PipeOut", type = STREAM)
    PipedOutputStream pipedOut = new PipedOutputStream();

    @BlockExecute
    public void process() {

        try{

            ObjectOutputStream objectOut = new ObjectOutputStream( pipedOut );

            String[] names  = {"cat1",  "cat2",   "cat3"};
            String[] colors = {"Black", "Yellow", "Gray"};

            for(int i = 0; i<3; i++){


                Cat cat = new Cat(names[i], colors[i], i+10);

                objectOut.writeObject(cat);
                objectOut.flush();
                Thread.sleep(500);
            }


            objectOut.close();
            pipedOut.close();

        } catch (Exception e){
            e.printStackTrace();
        }

    }




}





