/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package toniarts.opendungeonkeeper.tools.convert;

import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetManager;
import java.io.InputStream;
import toniarts.opendungeonkeeper.tools.convert.kmf.KmfFile;

/**
 * Small wrapper class to avoid writing and reading KMF files all over again
 * during conversion
 *
 * @author Toni Helenius <helenius.toni@gmail.com>
 */
public class KmfAssetInfo extends AssetInfo {

    private final KmfFile kmfFile;

    public KmfAssetInfo(AssetManager manager, AssetKey key, KmfFile kmfFile) {
        super(manager, key);
        this.kmfFile = kmfFile;
    }

    @Override
    public InputStream openStream() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    public KmfFile getKmfFile() {
        return kmfFile;
    }
}