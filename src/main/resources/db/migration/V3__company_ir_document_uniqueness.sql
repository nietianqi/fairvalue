SET search_path TO fairvalue, public;

CREATE UNIQUE INDEX IF NOT EXISTS uq_source_documents_source_url
    ON source_documents (source_id, document_url)
    WHERE document_url IS NOT NULL;
