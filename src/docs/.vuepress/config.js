module.exports = {
  base: "/shadow/",
  dest: "build/site",
  ga: "UA-321220-4",
  title: 'Gradle Shadow Plugin',
  themeConfig: {
    repo: "GradleUp/shadow",
    docsBranch: 'main',
    editLinks: true,
    editLinkText: 'Help improve these docs!',
    logo: '/logo+type.svg',
    docsDir: 'src/docs',
    nav: [
      { text: 'User Guide', link: '/introduction/' }
    ],
    sidebar: [
      '/',
      '/introduction/',
      '/getting-started/',
      '/configuration/',
      '/configuration/filtering/',
      '/configuration/dependencies/',
      '/configuration/merging/',
      '/configuration/relocation/',
      '/configuration/minimizing/',
      '/configuration/reproducible-builds/',
      '/custom-tasks/',
      '/application-plugin/',
      '/kmp-plugin/',
      '/publishing/',
      '/multi-project/',
      '/plugins/',
      '/changes/',
      '/about/'
    ]
  }
}
