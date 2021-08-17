package com.cosmos.thirdlive;

import android.content.Context;

import com.core.glcore.util.ImageFrame;
import com.cosmos.appbase.BeautyManager;
import com.cosmos.appbase.TransOesTextureFilter;
import com.cosmos.beauty.model.MMRenderFrameParams;
import com.cosmos.beauty.model.datamode.CommonDataMode;
import com.cosmos.beautyutils.Empty2Filter;
import com.cosmos.beautyutils.FaceInfoCreatorPBOFilter;
import com.cosmos.beautyutils.RotateFilter;

/**
 * 云信接入美颜sdk管理类
 */
public class NeteaseBeautyManager extends BeautyManager {
    private TransOesTextureFilter transOesTextureFilter;
    private RotateFilter rotateFilter;
    private RotateFilter backRotateFilter;
    private RotateFilter revertRotateFilter;
    private RotateFilter backRevertRotateFilter;

    public NeteaseBeautyManager(Context context) {
        super(context, cosmosAppid);
    }

    @Override
    public int renderWithOESTexture(int texture, int texWidth, int texHeight, boolean mFrontCamera, int cameraRotation) {
        if (transOesTextureFilter == null) {
            transOesTextureFilter = new TransOesTextureFilter();
        }
        int tempWidth = texWidth;
        int tempHeight = texHeight;
        if (cameraRotation == 90 || cameraRotation == 270) {
            tempHeight = texWidth;
            tempWidth = texHeight;
        }
        return renderWithTexture(transOesTextureFilter.newTextureReady(texture, texWidth, texHeight), tempWidth, tempHeight, mFrontCamera);
    }

    @Override
    public int renderWithTexture(int texture, int texWidth, int texHeight, boolean mFrontCamera) {
        if (resourceReady) {
            if (faceInfoCreatorPBOFilter == null) {
                rotateFilter = new RotateFilter(RotateFilter.ROTATE_90);
                backRotateFilter = new RotateFilter(RotateFilter.ROTATE_270);
                revertRotateFilter = new RotateFilter(RotateFilter.ROTATE_270);
                backRevertRotateFilter = new RotateFilter(RotateFilter.ROTATE_90);
                faceInfoCreatorPBOFilter = new FaceInfoCreatorPBOFilter(texWidth, texHeight);
                emptyFilter = new Empty2Filter();
                emptyFilter.setWidth(texWidth);
                emptyFilter.setHeight(texHeight);
            }
            RotateFilter tempRotateFilter;
            RotateFilter tempRevertRotateFilter;
            if (mFrontCamera) {
                tempRotateFilter = rotateFilter;
                tempRevertRotateFilter = revertRotateFilter;
            } else {
                tempRotateFilter = backRotateFilter;
                tempRevertRotateFilter = backRevertRotateFilter;
            }
            int rotateTexture = tempRotateFilter.rotateTexture(texture, texWidth, texHeight);
            faceInfoCreatorPBOFilter.newTextureReady(rotateTexture, emptyFilter, true);
            if (faceInfoCreatorPBOFilter.byteBuffer != null) {
                byte[] frameData = new byte[faceInfoCreatorPBOFilter.byteBuffer.remaining()];
                faceInfoCreatorPBOFilter.byteBuffer.get(frameData);
                //美颜sdk处理
                CommonDataMode dataMode = new CommonDataMode();
                dataMode.setNeedFlip(mFrontCamera);
                int beautyTexture = renderModuleManager.renderFrame(rotateTexture, new MMRenderFrameParams(
                        dataMode,
                        frameData,
                        texWidth,
                        texHeight,
                        texWidth,
                        texHeight,
                        ImageFrame.MMFormat.FMT_RGBA
                ));
                return tempRevertRotateFilter.rotateTexture(beautyTexture, texWidth, texHeight);
            }
        }
        return texture;
    }

    @Override
    public void textureDestoryed() {
        super.textureDestoryed();
        if (rotateFilter != null) {
            rotateFilter.destory();
            rotateFilter = null;
        }
        if (revertRotateFilter != null) {
            revertRotateFilter.destory();
            revertRotateFilter = null;
        }

    }
}
