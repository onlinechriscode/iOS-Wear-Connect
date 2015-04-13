package com.shiitakeo.android_wear_for_ios;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.util.Arrays;

/**
 * Created by kusabuka on 15/03/15.
 */
public class PacketProcessor {

    private enum PacketProcessingStatus {
        init,
        app_id,
        title,
        message,
        positiveAction,
        negativeAction,
        finish
    }

    private static final String TAG_LOG = "BLE_wear";

    private byte[] UID;
    private String appId;
    private String title;
    private String message;
    private String positiveAction;
    private String negativeAction;
    private ByteArrayOutputStream processingAttribute;

    private byte[] bytesFromPreviousPacket;
    private int bytesLeftToProcess;
    private int attributeBytesInNextPacket;

    private PacketProcessingStatus processingStatus;

    PacketProcessor() {
        processingAttribute = new ByteArrayOutputStream();
        init();
    }

    public void init() {
        processingStatus = PacketProcessingStatus.init;

        bytesLeftToProcess = 0;
        attributeBytesInNextPacket = 0;
        bytesFromPreviousPacket = new byte[] {};

        appId = null;
        title = null;
        message = null;
        positiveAction = null;
        negativeAction = null;
        processingAttribute.reset();
    }

    public byte[] getUID() {
        return UID;
    }

    public String getAppId() {
        return appId;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public String getPositiveAction() {
        return positiveAction;
    }

    public String getNegativeAction() {
        return negativeAction;
    }

    public boolean hasFinishedProcessing() {
        return processingStatus == PacketProcessingStatus.finish;
    }

    private int getAttributeLength(byte[] packet, int lengthIndex){
        //get att0's length
        byte[] byteLength = {packet[lengthIndex + 2], packet[lengthIndex + 1]};
        BigInteger length = new BigInteger(byteLength);
        return length.intValue();
    }

    public byte[] concat(byte[] a, byte[] b) {
        int aLen = a.length;
        int bLen = b.length;
        byte[] c = new byte[aLen+bLen];
        System.arraycopy(a, 0, c, 0, aLen);
        System.arraycopy(b, 0, c, aLen, bLen);
        return c;
    }

    private void updateProcessingStatus() {
        switch (processingStatus) {
            case init:
                Log.d(TAG_LOG, "$$$ init -> app_id.");
                processingStatus = PacketProcessingStatus.title;
                break;
            case app_id:
                Log.d(TAG_LOG, "$$$ finish app id reading.");
                processingStatus = PacketProcessingStatus.title;
                try {
                    appId = new String(processingAttribute.toByteArray(), "UTF-8");
                    processingAttribute.reset();
                    Log.d(TAG_LOG, "$$$ app_id : " + appId);
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
                break;
            case title:
                Log.d(TAG_LOG, "$$$ finish title  reading.");
                processingStatus = PacketProcessingStatus.message;
                try {
                    title = new String(processingAttribute.toByteArray(), "UTF-8");
                    processingAttribute.reset();
                    Log.d(TAG_LOG, "$$$ title : " + title);
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
                break;
            case message:
                Log.d(TAG_LOG, "$$ finish messgage  reading.");
                processingStatus = PacketProcessingStatus.positiveAction;
                try {
                    message = new String(processingAttribute.toByteArray(), "UTF-8");
                    processingAttribute.reset();
                    Log.d(TAG_LOG, "$$ message : " + message);
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
                break;
            case positiveAction:
                Log.d(TAG_LOG, "$$ finish messgage  reading.");
                processingStatus = PacketProcessingStatus.negativeAction;
                try {
                    positiveAction = new String(processingAttribute.toByteArray(), "UTF-8");
                    processingAttribute.reset();
                    Log.d(TAG_LOG, "$$ positiveAction : " + positiveAction);
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
                break;
            case negativeAction:
                Log.d(TAG_LOG, "$$ finish messgage  reading.");
                processingStatus = PacketProcessingStatus.finish;
                try {
                    negativeAction = new String(processingAttribute.toByteArray(), "UTF-8");
                    processingAttribute.reset();
                    Log.d(TAG_LOG, "$$ message : " + negativeAction);
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
                break;
            default:
                Log.d(TAG_LOG, "$$.");
                break;
        }
    }

    public void process(byte[] packet) {
        // Get size of received data
        packet = concat(bytesFromPreviousPacket, packet);
        bytesLeftToProcess = packet.length;
        bytesFromPreviousPacket = new byte[] {};

        int attributeIndex;

        while (bytesLeftToProcess > 0) {
            if (attributeBytesInNextPacket > 0) {
                // Still processing attribute started in a previous packet

                if (bytesLeftToProcess < attributeBytesInNextPacket) {
                    // The attribute is still not finished with this packet

                    // Save attribute data
                    processingAttribute.write(packet, 0, bytesLeftToProcess);

                    // Update bytes left of current attribute
                    attributeBytesInNextPacket -= bytesLeftToProcess;

                    // All bytes have been processed
                    bytesLeftToProcess = 0;
                }
                else {
                    // The attribute ends in this packet

                    // Save attribute data
                    processingAttribute.write(packet, 0, attributeBytesInNextPacket);

                    // There may be bytes of another attribute left in this packet
                    bytesLeftToProcess -= attributeBytesInNextPacket;

                    // This attribute's bytes have been processed
                    attributeBytesInNextPacket = 0;

                    if (bytesLeftToProcess > 0 && bytesLeftToProcess <= 2) {
                        // Not enough bytes to start processing next attribute

                        // Save bytes for next packet
                        bytesFromPreviousPacket = Arrays.copyOfRange(packet, attributeBytesInNextPacket, packet.length);
                        bytesLeftToProcess = 0;
                    }

                    updateProcessingStatus();
                }
            }
            else if (bytesLeftToProcess > 0) {
                // Attribute index
                if (processingStatus == PacketProcessingStatus.init) {
                    // Get Notification UID

                    UID = Arrays.copyOfRange(packet, 1, 5);

                    attributeIndex = 5;
                }
                else {
                    attributeIndex = packet.length - bytesLeftToProcess;
                }

                // Length of attribute to read
                int attributeLength = getAttributeLength(packet, attributeIndex);

                // Not counting bytes offering attribute length info
                int bytesInCurrentPacket = packet.length - (attributeIndex + 3);

                if (bytesInCurrentPacket < attributeLength) {
                    // The attribute is divided

                    // Save attribute data
                    processingAttribute.write(packet, attributeIndex + 3, bytesInCurrentPacket);

                    // Update bytes left of current attribute
                    attributeBytesInNextPacket = attributeLength - bytesInCurrentPacket;

                    if (processingStatus == PacketProcessingStatus.init) {
                        processingStatus = PacketProcessingStatus.app_id;
                    }

                    // All bytes have been processed
                    bytesLeftToProcess = 0;
                }
                else {
                    // The attribute ends in this packet

                    // Save attribute data
                    processingAttribute.write(packet, attributeIndex + 3, attributeLength);

                    // This attribute's bytes have been processed
                    attributeBytesInNextPacket = 0;

                    // There may be bytes of another attribute left in this packet
                    bytesLeftToProcess = bytesInCurrentPacket - attributeLength;

                    if (bytesLeftToProcess > 0 && bytesLeftToProcess <= 2) {
                        // Not enough bytes to start processing next attribute

                        // Offset of processed bytes
                        int offset = attributeIndex + 3 + attributeLength;

                        // Save bytes for next packet
                        bytesFromPreviousPacket = Arrays.copyOfRange(packet, offset, packet.length);
                        bytesLeftToProcess = 0;
                    }

                    updateProcessingStatus();
                }
            }
        }
    }
}
