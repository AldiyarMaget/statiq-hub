package kz.bns.hub.service;

public enum UserIntent {
    API_REQUEST,        // Вопросы про эндпоинты, Swagger, JSON, запросы
    ROLES_AND_ACCESS,   // Вопросы про роли, права доступа, полномочия, ROLES.md
    BDAP_PACKAGES,      // Вопросы про БДАП, пакеты, статусы загрузки, USER_GUIDE_BDAP.md
    METHODOLOGY_KSP,    // Вопросы про показатели, КСП, формы, USER_GUIDE.md
    GENERAL_SEARCH      // Обычный векторный поиск, если явного интента не обнаружено
}
