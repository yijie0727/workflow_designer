package cz.zcu.kiv.WorkflowDesigner;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface IRemoteData extends Remote {


    public BlockData getBlockData(String name ) throws RemoteException;

    public void setBlockData(BlockData blockData) throws RemoteException;

}
