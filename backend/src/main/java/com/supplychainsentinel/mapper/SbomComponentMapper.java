package com.supplychainsentinel.mapper;

import com.supplychainsentinel.dto.SbomComponentResponse;
import com.supplychainsentinel.entity.SbomComponent;

public final class SbomComponentMapper {

    private SbomComponentMapper() { }

    public static SbomComponentResponse toDto(SbomComponent c) {
        return new SbomComponentResponse(
                c.getId(),
                c.getBomRef(),
                c.getComponentType(),
                c.getGroupName(),
                c.getName(),
                c.getVersion(),
                c.getPurl(),
                c.getCpe(),
                c.getScope(),
                c.getSupplier(),
                c.getLicenses(),
                c.getHashes(),
                c.getCreatedAt()
        );
    }
}
