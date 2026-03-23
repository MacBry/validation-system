package com.mac.bry.validationsystem.audit;

import lombok.Value;

/**
 * Para: stan encji w danej rewizji + metadane rewizji.
 *
 * @param <T> typ encji (Validation, CoolingDevice, …)
 */
@Value
public class EntityRevisionEntry<T> {

    /** Stan encji zapisany w tej rewizji (null gdy DEL i store_data_at_delete=false) */
    T entity;

    RevisionInfoDto revisionInfo;
}
