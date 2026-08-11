package com.alicia.cloudstorage.rag.assistant;

public interface CandidateSearchPort {

    CandidateBindingResult search(CandidateSearchRequest request);
}
