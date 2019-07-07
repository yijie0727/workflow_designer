package cz.zcu.kiv.WorkflowDesigner;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

public class RemoteDataImple extends UnicastRemoteObject implements IRemoteData  {

    private static Log logger = LogFactory.getLog(RemoteDataImple.class);

    private BlockData blockData;

    public RemoteDataImple() throws RemoteException {
    }

    @Override
    public BlockData getBlockData (String blockIdName) throws RemoteException {
        logger.info("{Get} BlockData from "+ blockIdName);
        return blockData;
    }

    @Override
    public void setBlockData(BlockData blockData, String blockIdName) throws RemoteException {
        logger.info("(Set) BlockData for "+ blockIdName);
        this.blockData = blockData;
    }

}
