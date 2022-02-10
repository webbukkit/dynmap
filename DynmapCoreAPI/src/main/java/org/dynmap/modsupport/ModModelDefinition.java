package org.dynmap.modsupport;

/**
 * Model definition for a mod
 */
public interface ModModelDefinition {
    /**
     * Get mod ID
     * @return mod ID
     */
    public String getModID();
    /**
     * Get mod version
     * @return mod version
     */
    public String getModVersion();
    /**
     * Get texture definition associated with the model definition
     * @return texture definition
     */
    public ModTextureDefinition getTextureDefinition();
    /**
     * Add volumetric model : default assumes all metadata values are matching
     * @param blockid - block ID
     * @param scale - grid scale (subblock array is scale x scale x scale) : from 1 to 16
     * @return block model: use methods to set occupied subblocks
     */
    @Deprecated
    public VolumetricBlockModel addVolumetricModel(int blockid, int scale);
    /**
     * Add volumetric model : default assumes all metadata values are matching
     * @param blockname - block name
     * @param scale - grid scale (subblock array is scale x scale x scale) : from 1 to 16
     * @return block model: use methods to set occupied subblocks
     */
    @Deprecated
    public VolumetricBlockModel addVolumetricModel(String blockname, int scale);
    /**
     * Add standard stair model : default assumes all metadata values are matching
     * @param blockid - block ID
     * @return block model record
     */
    @Deprecated
    public StairBlockModel addStairModel(int blockid);
    /**
     * Add standard stair model : default assumes all metadata values are matching
     * @param blockname - block name
     * @return block model record
     */
    public StairBlockModel addStairModel(String blockname);
    /**
     * Add wall or fence model : default assumes all metadata values are matching
     * @param blockid - block ID
     * @param type - type of wall or fence
     * @return block model record
     */
    @Deprecated
    public WallFenceBlockModel addWallFenceModel(int blockid, WallFenceBlockModel.FenceType type);
    /**
     * Add wall or fence model : default assumes all metadata values are matching
     * @param blockname - block name
     * @param type - type of wall or fence
     * @return block model record
     */
    public WallFenceBlockModel addWallFenceModel(String blockname, WallFenceBlockModel.FenceType type);
    /**
     * Add cuboid model : default assumes all metadata values are matching
     * @param blockid - block ID
     * @return block model record
     */
    @Deprecated
    public CuboidBlockModel addCuboidModel(int blockid);
    /**
     * Add cuboid model : default assumes all metadata values are matching
     * @param blockname - block name
     * @return block model record
     */
    public CuboidBlockModel addCuboidModel(String blockname);
    /**
     * Add pane model : default assumes all metadata values are matching
     * @param blockid - block ID
     * @return block model record
     */
    @Deprecated
    public PaneBlockModel addPaneModel(int blockid);
    /**
     * Add pane model : default assumes all metadata values are matching
     * @param blockname - block name
     * @return block model record
     */
    public PaneBlockModel addPaneModel(String blockname);
    /**
     * Add standard plant model : default assumes all metadata values are matching
     * @param blockid - block ID
     * @return block model record
     */
    @Deprecated
    public PlantBlockModel addPlantModel(int blockid);
    /**
     * Add standard plant model : default assumes all metadata values are matching
     * @param blockname - block name
     * @return block model record
     */
    public PlantBlockModel addPlantModel(String blockname);
    /**
     * Add standard box model : default assumes all metadata values are matching
     * @param blockid - block ID
     * @return block model record
     */
    @Deprecated
    public BoxBlockModel addBoxModel(int blockid);
    /**
     * Add standard box model : default assumes all metadata values are matching
     * @param blockname - block name
     * @return block model record
     */
    public BoxBlockModel addBoxModel(String blockname);
    /**
     * Add door model
     * @param blockid - block ID
     * @return block model record
     */
    @Deprecated
    public DoorBlockModel addDoorModel(int blockid);
    /**
     * Add door model
     * @param blockname - block name
     * @return block model record
     */
    public DoorBlockModel addDoorModel(String blockname);
    /**
     * Add patch box model : default assumes all metadata values are matching
     * @param blockid - block ID
     * @return block model record
     */
    @Deprecated
    public PatchBlockModel addPatchModel(int blockid);
    /**
     * Add patch box model : default assumes all metadata values are matching
     * @param blockname - block name
     * @return block model record
     */
    public PatchBlockModel addPatchModel(String blockname);
    /**
     * Add rotated patch box model, based on existing model : default assumes all metadata values are matching
     * @param blockid - block ID
     * @param model - existing model to be rotated
     * @param xrot - x rotation in degrees (0, 90, 180, 270)
     * @param yrot - y rotation in degrees (0, 90, 180, 270)
     * @param zrot - z rotation in degrees (0, 90, 180, 270)
     * @return block model record
     */
    @Deprecated
    public PatchBlockModel addRotatedPatchModel(int blockid, PatchBlockModel model, int xrot, int yrot, int zrot);
    /**
     * Add rotated patch box model, based on existing model : default assumes all metadata values are matching
     * @param blockname - block name
     * @param model - existing model to be rotated
     * @param xrot - x rotation in degrees (0, 90, 180, 270)
     * @param yrot - y rotation in degrees (0, 90, 180, 270)
     * @param zrot - z rotation in degrees (0, 90, 180, 270)
     * @return block model record
     */
    public PatchBlockModel addRotatedPatchModel(String blockname, PatchBlockModel model, int xrot, int yrot, int zrot);
    /**
     * Add model block model: default assumes all metadata values are matching
     * @param blockname - block name
     * @return block model record
     */
    public ModelBlockModel addModelBlockModel(String blockname);
    /**
     * Final call for model definition: publishes definiiton to Dynmap to be used for the mod
     * @return true if successful, false if error
     */
    public boolean publishDefinition();
}
