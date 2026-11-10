package com.example.cabin4d.catalog

/**
 * 앱에서 바로 틀 수 있는 YouTube 영상 하나.
 * @param trackId 같은 영상의 4D 이펙트 트랙(assets/tracks/의 id). 없으면 4D 효과 없이 재생만 한다.
 */
data class YouTubeVideo(
    val videoId: String,
    val title: String,
    val channel: String,
    val year: Int,
    val blurb: String,
    val trackId: String? = null
) {
    /** 큰 것부터 시도할 썸네일 주소(큰 썸네일이 없는 영상이 있다) */
    val thumbnails: List<String>
        get() = listOf(
            "https://i.ytimg.com/vi/$videoId/maxresdefault.jpg",
            "https://i.ytimg.com/vi/$videoId/hq720.jpg",
            "https://i.ytimg.com/vi/$videoId/mqdefault.jpg"
        )
}

/**
 * YouTube 선반에 올리는 영상 목록. 지금은 Blender 공식 채널의 오픈 무비만 넣어 두었다.
 * 영상은 YouTube 공식 IFrame 플레이어로만 재생하고, 재생 위치는 플레이어 API에서 받아 4D 효과를 맞춘다.
 * 검색 기능은 YouTube Data API 키가 필요해서 아직 넣지 않았다.
 */
object YouTubeCatalog {
    val videos: List<YouTubeVideo> = listOf(
        YouTubeVideo("eRsGyueVLvQ", "Sintel", "Blender", 2010, "아기 용을 찾아 설산과 사막을 건너는 소녀의 모험", trackId = "sintel"),
        YouTubeVideo("R6MlUcmOul8", "Tears of Steel", "Blender", 2012, "로봇에게 점령된 암스테르담에서 벌어지는 마지막 작전", trackId = "tears-of-steel"),
        YouTubeVideo("YE7VzlLtp-4", "Big Buck Bunny", "Blender", 2008, "숲속 말썽꾸러기들에게 참다못한 커다란 토끼의 복수극", trackId = "big-buck-bunny"),
        YouTubeVideo("WhWc3b3KhnY", "Spring", "Blender Studio", 2019, "양치기 소녀와 개가 봄을 깨우러 산으로 간다"),
        YouTubeVideo("Y-rmzh0PI3c", "Cosmos Laundromat", "Blender", 2015, "외딴섬의 양 프랑크가 낯선 세일즈맨에게 다른 삶을 제안받는다"),
        YouTubeVideo("_cMxraX_5RE", "Sprite Fright", "Blender Studio", 2021, "숲에서 요정들을 만난 십대들의 호러 코미디"),
        YouTubeVideo("UXqq0ZvbOnk", "Charge", "Blender Studio", 2022, "에너지가 바닥난 세계, 배터리 공장에 숨어든 노인"),
        YouTubeVideo("u9lj-c29dxI", "Wing It!", "Blender Studio", 2023, "고양이와 개가 헛간에서 비행선을 띄우려는 소동"),
        YouTubeVideo("mN0zPOpADL4", "Agent 327: Operation Barbershop", "Blender Studio", 2017, "비밀 요원이 이발소에서 벌이는 액션 코미디"),
        YouTubeVideo("PVGeM40dABA", "Coffee Run", "Blender Studio", 2020, "커피 한 잔에 떠오른 기억 속을 달리는 여자"),
        YouTubeVideo("SkVqJ1SGeL0", "Caminandes 3: Llamigos", "Blender", 2016, "라마와 펭귄이 열매 하나를 두고 벌이는 한바탕 소동"),
        YouTubeVideo("TLkA0RELQ1g", "Elephants Dream", "Blender", 2006, "거대한 기계 속을 떠도는 두 남자. 세계 최초의 오픈 무비")
    )

    /** 직접 재생하는 4D 영화(trackId)에 대응하는 YouTube 영상 */
    fun forTrack(trackId: String): YouTubeVideo? = videos.firstOrNull { it.trackId == trackId }
}
