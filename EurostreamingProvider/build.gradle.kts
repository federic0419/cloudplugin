// use an integer for version numbers
version = 3

cloudstream {
    language = "it"
    // All of these properties are optional, you can safely remove them

    // description = "Lorem Ipsum"
    authors = listOf("Adippe")

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     * */
    status = 1 // will be 3 if unspecified
    tvTypes = listOf(
        "TvSeries"
    )


    iconUrl = "https://eurostreaming.observer/wp-content/uploads/2017/08/logo_upt_observer.png"
}
