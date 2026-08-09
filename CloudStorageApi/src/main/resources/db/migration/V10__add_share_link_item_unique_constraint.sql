ALTER TABLE share_link_item
    ADD CONSTRAINT uk_share_link_item_share_node UNIQUE (share_id, node_id);
