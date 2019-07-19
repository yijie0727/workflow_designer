package test;

import cz.zcu.kiv.WorkflowDesigner.Annotations.BlockExecute;
import cz.zcu.kiv.WorkflowDesigner.Annotations.BlockOutput;
import cz.zcu.kiv.WorkflowDesigner.Annotations.BlockType;

import java.io.ObjectOutputStream;
import java.io.PipedOutputStream;

import static cz.zcu.kiv.WorkflowDesigner.Type.STREAM;

@BlockType(type ="DataProvider2", family = "PIPE", continuousFlag = true)
public class DataProvider2 {

    @BlockOutput(name = "PipeOut", type = STREAM)
    PipedOutputStream pipedOut = new PipedOutputStream();

    @BlockExecute
    public void process() {

        try{

            ObjectOutputStream objectOut = new ObjectOutputStream( pipedOut );

            String[] names  = {"cat4",  "cat5",   "cat6"};
            String[] colors = {"White", "Blue", "Brown"};

            for(int i = 0; i<3; i++){


                Cat cat = new Cat(names[i], colors[i], i+20);

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
