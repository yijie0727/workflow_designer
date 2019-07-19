package test;

import cz.zcu.kiv.WorkflowDesigner.Annotations.BlockExecute;
import cz.zcu.kiv.WorkflowDesigner.Annotations.BlockOutput;
import cz.zcu.kiv.WorkflowDesigner.Annotations.BlockType;

import java.io.ObjectOutputStream;
import java.io.PipedOutputStream;

import static cz.zcu.kiv.WorkflowDesigner.Type.STREAM;

@BlockType(type ="DataProvider0", family = "PIPE", continuousFlag = true)
public class DataProvider0 {

    @BlockOutput(name = "PipeOut1", type = STREAM)
    PipedOutputStream pipedOut1 = new PipedOutputStream();


    @BlockOutput(name = "PipeOut2", type = STREAM)
    PipedOutputStream pipedOut2 = new PipedOutputStream();

    @BlockExecute
    public void process() {

        try{

            ObjectOutputStream objectOut1 = new ObjectOutputStream( pipedOut1 );
            ObjectOutputStream objectOut2 = new ObjectOutputStream( pipedOut2 );

            String[] names  = {"cat1",  "cat2",   "cat3",   "cat4",   "cat5",    "cat6"};
            String[] colors = {"Black",  "White", "White",  "Black",  "Black",   "White"};

            for(int i = 0; i<6; i++){


                Cat cat = new Cat(names[i], colors[i], i);
                if("Black".equals(cat.getColor())){
                    objectOut1.writeObject(cat);
                    objectOut1.flush();
                } else {
                    objectOut2.writeObject(cat);
                    objectOut2.flush();
                }

                Thread.sleep(500);
            }


            objectOut1.close();
            pipedOut1.close();
            objectOut2.close();
            pipedOut2.close();

        } catch (Exception e){
            e.printStackTrace();
        }

    }

}
