package de.hpi.isg.dataprep.metadata;

import de.hpi.isg.dataprep.exceptions.RuntimeMetadataException;
import de.hpi.isg.dataprep.model.repository.MetadataRepository;
import de.hpi.isg.dataprep.model.target.objects.ColumnMetadata;
import de.hpi.isg.dataprep.model.target.objects.MetadataOld;

/**
 * @author lan.jiang
 * @since 1/28/19
 */
public class PropertyExistence extends MetadataOld {

    private boolean propertyExist;

    public PropertyExistence(String propertyName, boolean propertyExist) {
        super(PropertyExistence.class.getSimpleName());
        this.propertyExist = propertyExist;
        this.scope = new ColumnMetadata(propertyName);
    }

    public boolean isPropertyExist() {
        return propertyExist;
    }

    @Override
    public void checkMetadata(MetadataRepository metadataRepository) throws RuntimeMetadataException {
//        List<PropertyExistence> matchedInRepo = metadataRepository.getMetadataPool().stream()
//                .filter(metadata -> metadata instanceof PropertyExistence)
//                .map(metadata -> (PropertyExistence) metadata)
//                .filter(metadata -> metadata.equals(this))
//                .collect(Collectors.toList());
//
//        if (matchedInRepo.size() == 0) {
//            throw new MetadataNotFoundException(String.format("MetadataOld %s not found in the repository.", this.toString()));
//        } else if (matchedInRepo.size() > 1) {
//            throw new DuplicateMetadataException(String.format("MetadataOld %s has multiple data type for property: %s",
//                    this.getClass().getSimpleName(), this.scope.getName()));
//        } else {
//            PropertyExistence metadataInRepo = matchedInRepo.get(0);
//            if (!this.equalsByValue(metadataInRepo)) {
//                // value of this metadata does not match that in the repository.
//                throw new MetadataNotMatchException(String.format("MetadataOld value does not match that in the repository."));
//            }
//        }
    }

    @Override
    public boolean equalsByValue(MetadataOld metadata) {
        return ((PropertyExistence)metadata).isPropertyExist() == this.propertyExist;
    }
}
