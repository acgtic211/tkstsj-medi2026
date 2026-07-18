package org.ual.spatialindex.rtree;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ual.spatialindex.rtreebase.*;
import org.ual.spatialindex.parameters.DatasetParameters;
import org.ual.spatialindex.spatialindex.*;
import org.ual.spatialindex.storagemanager.IStorageManager;
import org.ual.spatialindex.storagemanager.InvalidPageException;
import org.ual.spatialindex.storagemanager.PropertySet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Stack;
import java.util.function.Predicate;

/**
 * RTree implementation for spatial indexing.
 * This class extends AbstractRTree and provides methods for inserting, deleting,
 * and querying spatial data using the RTree structure.
 * <p>
 * The RTree supports various configurations through a PropertySet, allowing
 * customization of parameters such as leaf capacity, index capacity, fill factor,
 * and tree variant (Linear, Quadratic, or R*).
 * <p>
 * The class also includes methods for bulk loading data and managing tree statistics.
 *
 * @see AbstractRTree
 * @see ISpatialIndex
 */
public class RTree extends AbstractRTree {
    private static final Logger logger = LogManager.getLogger(RTree.class);

    // Fallback parameters (default values)
    private static final int DEFAULT_INDEX_CAPACITY = 50;
    private static final int DEFAULT_LEAF_CAPACITY = 50;
    private static final float DEFAULT_FILL_FACTOR = 0.7f;
    private static final float DEFAULT_SPLIT_DISTRIBUTION_FACTOR = 0.4f;
    private static final float DEFAULT_REINSERT_FACTOR = 0.3f;
    private static final int DEFAULT_NEAR_MINIMUM_OVERLAP_FACTOR = 32;
    private static final int DEFAULT_DIMENSION = 2;
    private static final int DEFAULT_TREE_VARIANT = SpatialIndex.RtreeVariantRstar;
    private static final float DEFAULT_ALPHA_DISTRIBUTION = 0.5f;

    public ArrayList<Data> pseudoNodes =  new ArrayList<>();

    public RTree(PropertySet propertySet, IStorageManager storageManager, DatasetParameters datasetParameters, boolean isDocumentAware) {
        super(storageManager, datasetParameters);
        this.rootID = IStorageManager.NewPage;
        this.infiniteRegion = new Region();
        this.stats = new Statistics();
        this.documentAwareEnabled = isDocumentAware;

        registerBaseDefaultValues();
        registerSubclassDefaultValues();

        propertySet.setProperty("IndexIdentifier", this.rootID);
        loadParameters(propertySet);
    }


    //====================================================================================
    //=========================== Default values registration ===========================
    //====================================================================================

    /**
     * Registers the base default values for the RTree configuration parameters.
     * This method initializes the default values map with commonly used parameters
     * such as leaf capacity, index capacity, dimension, fill factor, and tree variant.
     * It is called during the construction of the RTree instance.
     */
    private final void registerBaseDefaultValues() {
        defaultValues.put("LeafCapacity", DEFAULT_LEAF_CAPACITY);
        defaultValues.put("IndexCapacity", DEFAULT_INDEX_CAPACITY);
        defaultValues.put("Dimension", DEFAULT_DIMENSION);
        defaultValues.put("FillFactor", DEFAULT_FILL_FACTOR);
        defaultValues.put("SplitDistributionFactor", DEFAULT_SPLIT_DISTRIBUTION_FACTOR);
        defaultValues.put("ReinsertFactor", DEFAULT_REINSERT_FACTOR);
        defaultValues.put("NearMinimumOverlapFactor", DEFAULT_NEAR_MINIMUM_OVERLAP_FACTOR);
        defaultValues.put("TreeVariant", DEFAULT_TREE_VARIANT);
        defaultValues.put("AlphaDistribution", DEFAULT_ALPHA_DISTRIBUTION);

        logger.info("Base default values registered. Current defaults map: {}", defaultValues);
    }

    /**
      * Template method that allows subclasses to register their specific default values.
      * This method is called during RTree initialization after the base default values
      * have been registered.
      *
      * <p>Subclasses should override this method to add their own default values to the
      * {@code defaultValues} map. These values will be used as fallbacks when properties
      * are not explicitly provided in the PropertySet.
      *
      * <p>Example usage in a subclass:
      * <pre>
      * {@code
      * @Override
      * protected void registerSubclassDefaultValues() {
      *     defaultValues.put("CustomThreshold", 0.85);
      *     defaultValues.put("OptimizationMode", OptimizationMode.BALANCED);
      *     defaultValues.put("MaxRetries", 3);
      * }
      * }
      * </pre>
      *
      * @see #registerBaseDefaultValues() For registering base RTree default values
      * @see #getDefaultValueInternal(String, Class) For retrieving registered default values
      */
    protected void registerSubclassDefaultValues() {
        // Base implementation is empty. Subclasses will provide their registrations.
        logger.warn("No subclass default values registered by BaseClass's implementation.");
    }

    /**
     * Retrieves a default value of the expected type from the internal defaults map.
     * This method provides type-safe access to default values with runtime type checking
     * and automatic numeric conversions where appropriate.
     *
     * <p>The method performs the following validations and conversions:
     * <ul>
     *   <li>Checks if the requested key exists in the defaults map
     *   <li>Validates that the stored value matches or can be converted to the expected type
     *   <li>Handles null values appropriately for primitive vs reference types
     *   <li>Supports numeric type conversions (e.g., Integer to Float) with precision loss warnings
     * </ul>
     *
     * @param <T> The expected return type
     * @param key The configuration key to look up
     * @param expectedType The Class object representing the expected return type
     * @return The default value cast or converted to the expected type
     * @throws IllegalArgumentException if no default value exists for the given key
     * @throws IllegalStateException if the stored value cannot be converted to the expected type
     */
    private <T> T getDefaultValueInternal(String key, Class<T> expectedType) {
        if (!defaultValues.containsKey(key)) {
            logger.error("No default value defined for key: '{}' of expected type {}.", key, expectedType.getName());
            throw new IllegalArgumentException(
                    String.format("No default value defined for key: '%s' in the configuration hierarchy.", key));
        }

        Object value = defaultValues.get(key);

        if (value == null) {
            if (!expectedType.isPrimitive() || expectedType == Void.class) { // Void.class check for theoretical null for void type
                return null; // Allow null for non-primitive types if explicitly set as default
            } else {
                logger.error("Default value for key '{}' is null, which is not permissible for primitive type {}.", key, expectedType.getName());
                throw new IllegalStateException(
                        String.format("Default value for key '%s' is null, but expected primitive type %s.", key, expectedType.getName()));
            }
        }

        // Direct type match
        if (expectedType.isInstance(value)) {
            return expectedType.cast(value);
        }

        // --- Flexible Numeric Conversion Logic ---
        if (value instanceof Number) {
            Number numValue = (Number) value;
            if (expectedType == Double.class) {
                logger.warn("Converting default Number '{}' of type {} to Double for key '{}'.",
                        numValue, value.getClass().getSimpleName(), key);
                return expectedType.cast(numValue.doubleValue());
            }
            if (expectedType == Float.class) {
                logger.warn("Converting default Number '{}' of type {} to Float for key '{}'. Potential precision loss if original was Double.",
                        numValue, value.getClass().getSimpleName(), key);
                return expectedType.cast(numValue.floatValue());
            }
            if (expectedType == Integer.class) {
                logger.warn("Converting default Number '{}' of type {} to Integer for key '{}'.",
                        numValue, value.getClass().getSimpleName(), key);
                return expectedType.cast(numValue.intValue());
            }
        }
        // --- End Flexible Numeric Conversion Logic ---


        // If no direct match and no conversion path was taken:
        String valueTypeName = value.getClass().getName();
        logger.error("Default value type mismatch for key '{}'. Expected: {}, Actual: {}. No automatic conversion applied.",
                key, expectedType.getName(), valueTypeName);
        throw new IllegalStateException(
                String.format("Default value for key '%s' is of type %s, but expected %s. Check default value registration or ensure appropriate conversion logic exists.",
                        key, valueTypeName, expectedType.getName()));
    }


    /**
     * Retrieves an integer property from the PropertySet, validates it using the provided predicate,
     * and returns its value. If the property is not found or invalid, falls back to a default value.
     *
     * @param propertySet The PropertySet containing the properties
     * @param key The key of the property to retrieve
     * @param validator A predicate to validate the property value
     * @param errorMessage The error message to throw if validation fails
     * @return The validated integer property value
     */
    protected int getIntegerProperty(PropertySet propertySet, String key, Predicate<Integer> validator, String errorMessage) {
        Object var = propertySet.getProperty(key);
        if (var != null) {
            if (var instanceof Integer) {
                int value = (Integer) var;
                if (validator.test(value)) {
                    return value;
                }
                throw new IllegalArgumentException(String.format("%s (Invalid value from PropertySet: %d for key '%s')", errorMessage, value, key));
            } else {
                logger.warn("Property '{}' in PropertySet is of type {} with value '{}' but expected Integer. Using default.", key, var.getClass().getName(), var);
            }
        }

        int defaultValue = getDefaultValueInternal(key, Integer.class);
        logger.warn("Property '{}' not found in PropertySet or incompatible type. Using default value. {}", key, defaultValue);
        if (validator.test(defaultValue)) {
            return defaultValue;
        }
        throw new IllegalStateException(String.format("Default value for key '%s' (%d) failed validation: %s", key, defaultValue, errorMessage));
    }


    /**
     * Retrieves an integer property from the PropertySet, validates it using the provided predicate,
     * and returns its value. If the property is not found or invalid, falls back to a default value.
     * This method is designed to handle cases where the property might be of type Integer or Number.
     *
     * @param propertySet The PropertySet containing the properties
     * @param key The key of the property to retrieve
     * @param validator A predicate to validate the property value
     * @param errorMessage The error message to throw if validation fails
     * @return The validated integer property value
     */
    protected double getDoubleProperty(PropertySet propertySet, String key, Predicate<Double> validator, String errorMessage) {
        Object var = propertySet.getProperty(key);
        if (var != null) {
            if (var instanceof Double) {
                double value = (Double) var;
                if (validator.test(value)) {
                    return value;
                }
                throw new IllegalArgumentException(String.format("%s (Invalid value from PropertySet: %f for key '%s')", errorMessage, value, key));
            } else if (var instanceof Number) { // Handle if it's an Integer that can be a Double
                double value = ((Number) var).doubleValue();
                if (validator.test(value)) {
                    return value;
                }
                throw new IllegalArgumentException(String.format("%s (Invalid value (converted from Number) from PropertySet: %f for key '%s')", errorMessage, value, key));
            }
            else {
                logger.warn("Property '{}' in PropertySet is of type {} but expected Double or Number. Using default.", key, var.getClass().getName());
            }
        }

        double defaultValue = getDefaultValueInternal(key, Double.class);
        logger.warn("Property '{}' not found in PropertySet or incompatible type. Using default value: {}.", key, defaultValue);
        if (validator.test(defaultValue)) {
            return defaultValue;
        }
        throw new IllegalStateException(String.format("Default value for key '%s' (%f) failed validation: %s", key, defaultValue, errorMessage));
    }

    /**
     * Retrieves a float property from the PropertySet, validates it using the provided predicate,
     * and returns its value. If the property is not found or invalid, falls back to a default value.
     * This method is designed to handle cases where the property might be of type Float or Number.
     *
     * @param propertySet The PropertySet containing the properties
     * @param key The key of the property to retrieve
     * @param validator A predicate to validate the property value
     * @param errorMessage The error message to throw if validation fails
     * @return The validated float property value
     */
    protected float getFloatProperty(PropertySet propertySet, String key, Predicate<Float> validator, String errorMessage) {
        Object var = propertySet.getProperty(key);
        if (var != null) {
            if (var instanceof Float) {
                float value = (Float) var;
                if (validator.test(value)) {
                    return value;
                }
                throw new IllegalArgumentException(String.format("%s (Invalid value from PropertySet: %f for key '%s')", errorMessage, value, key));
            } else if (var instanceof Number) { // Handle Double, Integer etc. from PropertySet and convert to float
                Number numVar = (Number) var;
                float value = numVar.floatValue();
                // Log if there might be precision loss, e.g., if numVar was a Double with more precision
                if (numVar instanceof Double && ((Double)numVar).doubleValue() != (double)value) {
                    logger.warn("Property '{}' in PropertySet (a Double: {}) was converted to Float: {}. Potential precision loss.", key, numVar, value);
                } else {
                    logger.warn("Property '{}' in PropertySet is of type {}, converting to Float: {}.", key, var.getClass().getName(), value);
                }

                if (validator.test(value)) {
                    return value;
                }
                throw new IllegalArgumentException(String.format("%s (Invalid value (converted from Number %s) from PropertySet: %f for key '%s')", errorMessage, var.getClass().getSimpleName(), value, key));
            } else {
                logger.warn("Property '{}' in PropertySet is of type {} but expected Float or Number. Using default.", key, var.getClass().getName());
            }
        }

        float defaultValue = getDefaultValueInternal(key, Float.class);
        logger.warn("Property '{}' not found in PropertySet or incompatible type. Using default value for key '{}'.", key, defaultValue);
        if (validator.test(defaultValue)) {
            return defaultValue;
        }
        throw new IllegalStateException(String.format("Default value for key '%s' (%f) failed validation: %s", key, defaultValue, errorMessage));
    }


    /**
     * Retrieves an enum property from the PropertySet, validates it using the provided predicate,
     * and returns its value. If the property is not found or invalid, falls back to a default value.
     * This method supports both String representations and direct enum instances.
     *
     * @param <E> The type of the enum
     * @param propertySet The PropertySet containing the properties
     * @param key The key of the property to retrieve
     * @param enumClass The Class object representing the enum type
     * @param validator A predicate to validate the enum value
     * @param errorMessage The error message to throw if validation fails
     * @return The validated enum property value
     */
    protected <E extends Enum<E>> E getEnumProperty(PropertySet propertySet, String key, Class<E> enumClass, Predicate<E> validator, String errorMessage) {
        Object var = propertySet.getProperty(key);
        E valueToValidate = null;

        if (var != null) {
            if (enumClass.isInstance(var)) {
                valueToValidate = enumClass.cast(var);
            } else if (var instanceof String) {
                try {
                    valueToValidate = Enum.valueOf(enumClass, (String) var);
                } catch (IllegalArgumentException e) {
                    logger.warn("Property '{}' in PropertySet is a String '{}' which is not a valid constant for enum {}. Using default.", key, var, enumClass.getSimpleName());
                }
            } else {
                logger.warn("Property '{}' in PropertySet is of type {} but expected {} or String. Using default.", key, var.getClass().getName(), enumClass.getSimpleName());
            }

            if (valueToValidate != null) {
                if (validator.test(valueToValidate)) {
                    return valueToValidate;
                }
                throw new IllegalArgumentException(String.format("%s (Invalid value from PropertySet: %s for key '%s')", errorMessage, valueToValidate.name(), key));
            }
        }

        logger.warn("Property '{}' not found, incompatible, or failed conversion in PropertySet for enum {}. Using default.", key, enumClass.getSimpleName());
        E defaultValue = getDefaultValueInternal(key, enumClass);
        if (validator.test(defaultValue)) {
            return defaultValue;
        }
        throw new IllegalStateException(String.format("Default value for key '%s' (%s) for enum %s failed validation: %s", key, defaultValue.name(), enumClass.getSimpleName(), errorMessage));
    }



    //==========================================================================================
    //=========================== Parameter loading and validation =============================
    //==========================================================================================


    /**
     * Loads and validates configuration parameters for the RTree from the provided property set.
     * If a parameter is missing or invalid in the property set, falls back to predefined default values.
     * <p>
     * Parameters loaded include:
     * - Tree variant (Linear, Quadratic, or R* variant)
     * - Node capacities (index and leaf)
     * - Tree dimension
     * - Fill factor (node utilization threshold)
     * - Split distribution factor (for node splitting)
     * - Reinsert factor (for forced reinsertions)
     * - Alpha distribution (for split axis selection)
     *
     * @param propertySet The property set containing RTree configuration parameters
     * @throws IllegalArgumentException if any property value fails validation against its constraints
     * @throws IllegalStateException if a default value fails validation
     * @see #getIntegerProperty
     * @see #getFloatProperty
     * @see #registerBaseDefaultValues
     */
    @Override
    protected void loadParameters(PropertySet propertySet) {
        // Load integer properties with validation
        treeVariant = getIntegerProperty(propertySet, "TreeVariant", i ->
                        i == SpatialIndex.RtreeVariantLinear ||
                                i == SpatialIndex.RtreeVariantQuadratic ||
                                i == SpatialIndex.RtreeVariantRstar,
                "Property TreeVariant not a valid variant");

        indexCapacity = getIntegerProperty(propertySet, "IndexCapacity", i -> i >= 3,
                "Property IndexCapacity must be >= 3");

        leafCapacity = getIntegerProperty(propertySet, "LeafCapacity", i -> i >= 3,
                "Property LeafCapacity must be >= 3");

        nearMinimumOverlapFactor = getIntegerProperty(propertySet, "NearMinimumOverlapFactor", i ->
                        i >= 1 && i <= indexCapacity && i <= leafCapacity,
                "Property NearMinimumOverlapFactor must be less than both index and leaf capacities");

        dimension = getIntegerProperty(propertySet, "Dimension", i -> i > 1,
                "Property Dimension must be >= 1");

        // Load float properties with validation
        fillFactor = getFloatProperty(propertySet, "FillFactor", f -> f > 0.0 && f < 1.0,
                "Property FillFactor must be in (0.0, 1.0)");

        splitDistributionFactor = getFloatProperty(propertySet, "SplitDistributionFactor", f -> f > 0.0 && f < 1.0,
                "Property SplitDistributionFactor must be in (0.0, 1.0)");

        reinsertFactor = getFloatProperty(propertySet, "ReinsertFactor", f -> f > 0.0f && f < 1.0f,
                "Property ReinsertFactor must be in (0.0, 1.0)");

        alphaDistribution = getFloatProperty(propertySet, "AlphaDistribution", f -> f >= 0.0f && f <= 1.0f,
                "Property AlphaDistribution must be in [0.0, 1.0]");

        initializeInfiniteRegion();
        initializeRootNode();
    }


    /**
     * Initializes the infinite region for the RTree.
     * The infinite region represents a special marker that indicates an unbounded or
     * undefined spatial region in the coordinate system.
     *
     * <p>For each dimension in the RTree, this method:
     * - Sets the lower bound to positive infinity
     * - Sets the upper bound to negative infinity
     *
     * <p>This creates an "invalid" region (since lower > upper) which can be used to:
     * - Mark uninitialized or undefined spatial regions
     * - Act as a sentinel value in spatial comparisons
     * - Support special cases in spatial query algorithms
     *
     * @see Region The spatial region class that represents bounded areas in the RTree
     * @see #dimension The number of dimensions in this RTree instance
     */
    private void initializeInfiniteRegion() {
        // Initialize the infinite region with positive and negative infinities
        infiniteRegion = new Region(dimension);

        // Set low and high values to positive and negative infinity respectively
        for (int dim = 0; dim < dimension; dim++) {
            infiniteRegion.setLow(dim, Double.POSITIVE_INFINITY);
            infiniteRegion.setHigh(dim, Double.NEGATIVE_INFINITY);
        }
    }


    /**
      * Initializes the root node as an empty leaf and persists it.
      *
      * <p>This initialization:
      * <ul>
      *   <li>Sets tree height to {@code 1}</li>
      *   <li>Initializes level {@code 0} node statistics</li>
      *   <li>Creates a {@link Leaf} with parent {@code -1}</li>
      *   <li>Stores the node and updates {@code rootID}</li>
      * </ul>
      *
      * @see Leaf
      * @see #writeNode(Node)
      * @see Statistics#setTreeHeight(int)
      * @see Statistics#addNodesInLevel(int, int)
      */
    private void initializeRootNode() {
        stats.setTreeHeight(1);
        stats.addNodesInLevel(0, 0);  // Initialize level 0 with 0 nodes (writeNode will increment to 1)
        Leaf root = new Leaf(this, -1);
        rootID = writeNode(root);
    }



    //==========================================================================================
    //================================ ISpatialIndex Interface =================================
    //==========================================================================================

    /**
      * Inserts data with associated document references into the RTree.
      * This operation is not supported in the current implementation to maintain simplicity
      * and consistency with the base RTree functionality.
      *
      * <p>For inserting spatial data without document references, use the standard
      * {@link #insertData(int, IShape)} method instead.
      *
      * @param id    The unique identifier for the data entry
      * @param shape The spatial shape defining the object's geometry and location
      * @param doc   A set of document IDs that reference this spatial object
      * @throws UnsupportedOperationException Always thrown since document-based insertion
      *         is not supported in this implementation
      * @see #insertData(int, IShape) The standard insertion method to use instead
      * @since 1.0
      * @deprecated Use {@link #insertData(int, IShape)} instead. Document references
      *             should be managed through a separate index structure.
      */
    @Override
    public void insertData(int id, final IShape shape, HashSet<Integer> doc) {
        throw new UnsupportedOperationException("This method is not supported. Use insertData without document parameter instead.");
    }


    /**
      * Inserts data into the RTree without document references.
      * This method is a placeholder for the unsupported version of data insertion
      * that includes document IDs. It throws an UnsupportedOperationException to
      * indicate that this functionality is not available in the current implementation.
      *
      * @param id The unique identifier for the data entry
      * @param region The spatial region representing the data entry's geometry
      * @param doc A set of document IDs associated with the data entry (not used)
      * @throws UnsupportedOperationException This method is not implemented - use
      *         {@link #insertDataImpl(int, Region)} for data insertion
      */
    @Override
    protected void insertDataImpl(int id, Region region, HashSet<Integer> doc) {
        throw new UnsupportedOperationException("This method is not supported. Use the version without document parameter instead.");
    }


    /**
      * Inserts data into the RTree with spatial indexing.
      * <p>
      * This method serves as the main entry point for data insertion operations.
      * It performs the following steps:
      * <ol>
      *   <li>Validates the input shape's dimension against the tree's dimension</li>
      *   <li>Extracts the minimum bounding rectangle (MBR) from the shape</li>
      *   <li>Delegates to insertDataImpl for the actual insertion process</li>
      * </ol>
      *
      * @param id    The unique identifier for the data entry
      * @param shape The spatial shape representing the object's geometry, must have
      *             the same dimension as the RTree's configured dimension
      * @throws IllegalArgumentException if the shape's dimension does not match
      *         the tree's configured dimension
      * @throws NullPointerException if shape is null
      * @see #insertDataImpl(int, Region)
      */
    @Override
    public void insertData(int id, final IShape shape) {
        // Validate dimension early to fail fast
        if (shape.getDimension() != dimension) {
            throw new IllegalArgumentException("insertData: Shape has the wrong number of dimensions.");
        }

        // Extract MBR once and reuse
        Region mbr = shape.getMBR();

        // Delegate to implementation method
        insertDataImpl(id, mbr);
    }


    /**
      * Implementation of data insertion into the RTree.
      * This method handles the actual insertion process by:
      * 1. Creating a path buffer to track the insertion path
      * 2. Reading the root node
      * 3. Finding the appropriate leaf node
      * 4. Performing the insertion with overflow handling
      * 5. Updating tree statistics
      *
      * @param id    The unique identifier for the data entry
      * @param mbr   The minimum bounding rectangle of the data being inserted.
      *              Must have the same dimension as the tree
      * @throws IllegalArgumentException if the mbr's dimension doesn't match
      *         the tree's configured dimension or if id is invalid
      * @throws InvalidPageException if there's an error reading/writing nodes
      * @throws AssertionError if mbr dimension validation fails
      */
    protected void insertDataImpl(int id, Region mbr) {
        assert mbr.getDimension() == dimension;

        // Create path buffer to track insertion path
        Stack<Integer> pathBuffer = new Stack<>();

        // Read the root node
        Node root = readNode(rootID);

        // Initialize overflow table based on tree height
        HashMap<Integer, Boolean> overflowTable = new HashMap<>(stats.getTreeHeight());
        for (int i = 0; i < stats.getTreeHeight(); i++) {
            overflowTable.put(i, false); // Initialize all levels (0 = leaf, n = root) as not overflowing
        }

        // Find the appropriate leaf node for insertion
        Node leaf = root.chooseSubtree(mbr, 0, pathBuffer);

        // Insert the data into the chosen leaf
        leaf.insertData(id, mbr, pathBuffer, overflowTable);

        // Update statistics
        stats.incrementData();
    }


    /**
      * Inserts data into the RTree at a specific level of the tree hierarchy.
      * This specialized insertion method provides fine-grained control over the insertion
      * process by allowing data to be placed at a specified tree level. This is particularly
      * useful for bulk loading and tree reorganization operations.
      *
      * @param objectId      The unique identifier for the data entry. Used to reference
      *                      the object within the tree structure
      * @param mbr           The minimum bounding rectangle that spatially contains the data.
      *                      Must have the same dimension as the tree's configured dimension
      * @param level         The target level in the tree where the data should be inserted.
      *                      Level 0 represents leaf nodes, with higher values indicating
      *                      internal nodes closer to the root
      * @param overflowTable A HashMap tracking node overflow status at each tree level.
      *                      Keys are level numbers and values indicate overflow status.
      *                      Used during reinsertions and splits to prevent cascading overflows
      * @throws IllegalArgumentException if the mbr's dimension doesn't match the tree's dimension
      *                                 or if the specified level is invalid
      */
    @Override
    protected void insertDataImpl(int objectId, Region mbr, int level, HashMap<Integer, Boolean> overflowTable) {
        assert mbr.getDimension() == dimension;

        // Pre-allocate the path buffer with estimated capacity to reduce resizing
        Stack<Integer> pathBuffer = new Stack<>();
        pathBuffer.ensureCapacity(stats.getTreeHeight());

        // Read root node once and reuse
        Node root = readNode(rootID);

        // Choose the appropriate node at the specified level for insertion
        Node targetNode = root.chooseSubtree(mbr, level, pathBuffer);

        // Insert the data at the chosen node
        targetNode.insertData(objectId, mbr, pathBuffer, overflowTable);
    }


    /**
      * Deletes data from the RTree based on its spatial shape and ID.
      * <p>
      * The deletion process involves:
      * <ol>
      *   <li>Validating shape dimensions and non-null parameters</li>
      *   <li>Converting shape to its minimum bounding rectangle (MBR)</li>
      *   <li>Locating the leaf node containing the data</li>
      *   <li>Removing the entry and rebalancing the tree if needed</li>
      *   <li>Updating tree statistics</li>
      * </ol>
      * <p>
      * Note: Deletion requires an exact match of both shape and ID. The shape must be
      * identical to the one used during insertion - partial overlaps are not considered
      * matches.
      *
      * @param id    The unique identifier of the data entry to delete
      * @param shape The spatial shape to delete, must match insertion shape exactly
      * @return      true if data was found and deleted, false if not found
      * @throws IllegalArgumentException if shape dimension != tree dimension
      * @throws NullPointerException if shape is null
      * @throws InvalidPageException if node access fails during deletion
      * @see #deleteDataImpl(int, Region) For the internal deletion implementation
      */
    @Override
    public boolean deleteData(int id, IShape shape) {
        // Validate input parameters
        if (shape == null) {
            throw new NullPointerException("deleteData: Shape cannot be null.");
        }

        // Validate dimension early to fail fast
        if (shape.getDimension() != dimension) {
            throw new IllegalArgumentException("deleteData: Shape has the wrong number of dimensions.");
        }

        // Extract MBR once and reuse
        Region mbr = shape.getMBR();

        // Delegate to implementation method
        return deleteDataImpl(id, mbr);
    }


    /**
      * Implements the data deletion logic for the RTree.
      * This internal method handles the low-level deletion process after input validation.
      * <p>
      * Deletion process steps:
      * <ol>
      *   <li>Verifies MBR dimension matches tree dimension (via assertion)</li>
      *   <li>Initializes path buffer to track traversal during:
      *     <ul>
      *       <li>Node merging</li>
      *       <li>MBR recalculation</li>
      *       <li>Tree rebalancing</li>
      *     </ul>
      *   </li>
      *   <li>Traverses tree to locate target leaf node</li>
      *   <li>Removes data entry and performs necessary tree maintenance:
      *     <ul>
      *       <li>Updates parent MBRs</li>
      *       <li>Handles underflow conditions</li>
      *       <li>Merges nodes if needed</li>
      *     </ul>
      *   </li>
      *   <li>Updates tree statistics on successful deletion</li>
      * </ol>
      *
      * @param id  The unique identifier of the data entry to delete
      * @param mbr The minimum bounding rectangle of the data to delete. Must have
      *           the same dimension as the tree's configured dimension
      * @return true if the data was successfully deleted, false if not found
      * @throws AssertionError if the MBR dimension doesn't match tree dimension
      * @throws InvalidPageException if node read/write operations fail
      * @see #deleteData(int, IShape) The public deletion interface
      * @since 1.0
      */
    protected boolean deleteDataImpl(int id, final Region mbr) {
        assert mbr.getDimension() == dimension;

        // Create path buffer to track traversal path for potential rebalancing
        Stack<Integer> pathBuffer = new Stack<>();
        pathBuffer.ensureCapacity(stats.getTreeHeight()); // Pre-allocate capacity to minimize resizing

        // Read root node once and reuse
        Node root = readNode(rootID);

        // Handle empty tree case
        if (root == null) {
            return false;
        }

        // Find the leaf containing the target data entry
        Leaf targetLeaf = root.findLeaf(id, mbr, pathBuffer);

        // Perform deletion if the entry was found
        if (targetLeaf != null) {
            targetLeaf.deleteData(id, pathBuffer);
            stats.decrementData();
            return true;
        }

        return false;
    }


    /**
      * Returns a detailed string representation of the RTree's configuration and state.
      * The returned string includes:
      * <ul>
      *   <li>Basic parameters: dimension, fill factor, node capacities</li>
      *   <li>R*-tree specific parameters (if applicable): overlap factor, reinsert factor, etc.</li>
      *   <li>Current utilization percentage of leaf nodes</li>
      *   <li>Tree statistics: node counts, tree height, data entries</li>
      * </ul>
      *
      * @return a multi-line string containing the RTree's parameters, statistics and
      *         current space utilization percentage
      * @see Statistics#toString() for details about included tree statistics
      */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        // Append basic tree parameters
        sb.append("Dimension: ").append(dimension).append('\n')
                .append("Fill factor: ").append(fillFactor).append('\n')
                .append("Index capacity: ").append(indexCapacity).append('\n')
                .append("Leaf capacity: ").append(leafCapacity).append('\n');

        // Append R*-tree specific parameters if applicable
        if (treeVariant == SpatialIndex.RtreeVariantRstar) {
            sb.append("Near minimum overlap factor: ").append(nearMinimumOverlapFactor).append('\n')
                    .append("Reinsert factor: ").append(reinsertFactor).append('\n')
                    .append("Split distribution factor: ").append(splitDistributionFactor).append('\n')
                    .append("Alpha distribution: ").append(alphaDistribution).append('\n');
        }

        // Calculate and append utilization percentage, avoiding division by zero
        long leafNodes = stats.getNumberOfNodesInLevel(0);
        String utilization = (leafNodes > 0)
                ? String.format("%.1f%%", 100.0 * stats.getNumberOfData() / ((double) leafNodes * leafCapacity))
                : "N/A";
        sb.append("Utilization: ").append(utilization).append('\n')
                .append(stats);

        return sb.toString();
    }


    //==========================================================================================
    //================================= Bulk loading methods ===================================
    //==========================================================================================

    /**
      * Stores a data entry in preparation for bulk loading into the RTree.
      * This method temporarily stores spatial data entries in an internal collection
      * used during the bulk loading process. Bulk loading is significantly more efficient
      * than sequential insertions when loading large amounts of data into an empty tree,
      * as it:
      * <ul>
      *   <li>Minimizes node splits and reorganizations</li>
      *   <li>Optimizes node utilization and tree structure</li>
      *   <li>Reduces I/O operations during tree construction</li>
      * </ul>
      *
      * <p>The stored entries will be processed by the bulk loading algorithm when
      * {@link #bulkLoadRTree(BulkLoadMethod)} is called. Currently supports the
      * Sort-Tile-Recursive (STR) bulk loading algorithm for efficient spatial data organization.
      *
      * @param region The spatial region representing the data entry's geometry.
      *              Must have the same dimension as the RTree and must not be null
      * @param id     The unique identifier for the entry. Used for referencing and
      *              retrieving the object within the tree structure
      * @throws IllegalArgumentException if region's dimension doesn't match tree dimension
      * @throws NullPointerException if region is null
      * @see #bulkLoadRTree(BulkLoadMethod) for processing stored entries
      * @see BulkLoadMethod for supported bulk loading methods
      */
    public void storePseudoNodes(int id, Region region) {
        pseudoNodes.add(new Data(id, region));
    }


    /**
      * Performs bulk loading of the RTree using the specified method.
      * Bulk loading is more efficient than sequential insertion when loading large amounts of data
      * into an empty tree. The bulk loading process optimizes node utilization and minimizes
      * I/O operations during tree construction.
      *
      * <p>Currently supports the STR (Sort-Tile-Recursive) algorithm, which:
      * <ol>
      *   <li>Sorts the data along each dimension using spatial coordinates</li>
      *   <li>Partitions the sorted data into tiles based on node capacity</li>
      *   <li>Recursively builds tree levels bottom-up to ensure balanced structure</li>
      *   <li>Optimizes node fill factors to improve search performance</li>
      * </ol>
      *
      * <p>Before calling this method, data entries must be stored using
      * {@link #storePseudoNodes(int, Region)}. The effective node capacities
      * are calculated using the tree's fill factor to ensure optimal space utilization.
      *
      * @param bulkLoadMethod The bulk loading algorithm to use (currently only STR is supported)
      * @throws IllegalArgumentException if an unsupported bulk load method is specified
      * @throws IllegalStateException if called when pseudoNodes collection is empty
      * @see BulkLoadMethod#STR for details about the STR algorithm
      * @see #storePseudoNodes(int, Region) for adding data before bulk loading
      */
    public void bulkLoadRTree(BulkLoadMethod bulkLoadMethod) {
        // Calculate effective node capacities based on fill factor
        int effectiveIndexCapacity = (int) Math.floor(indexCapacity * fillFactor);
        int effectiveLeafCapacity = (int) Math.floor(leafCapacity * fillFactor);

        if (pseudoNodes.isEmpty()) {
            logger.error("bulkLoadRTree: No data to load into the tree.");
            return;
        }

        if (bulkLoadMethod == BulkLoadMethod.STR) {
            BulkLoader bulkLoader = new BulkLoader();
            bulkLoader.bulkLoadUsingSTR(this, pseudoNodes, effectiveIndexCapacity, effectiveLeafCapacity);

            // Clear pseudoNodes collection to free memory after bulk loading
            pseudoNodes.clear();
            pseudoNodes.trimToSize();
        } else {
            logger.error("bulkLoadRTree: Unsupported bulk load method: {}", bulkLoadMethod);
            throw new IllegalArgumentException("bulkLoadRTree: Unsupported bulk load method: " + bulkLoadMethod);
        }
    }
}
