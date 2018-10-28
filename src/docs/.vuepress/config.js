module.exports = {
  base: "/shadow-new-site/",
  dest: "build/site",
  themeConfig: {
    repo: "johnrengelman/shadow",
    editLinks: true,
    editLinkText: 'Help improve these docs!',
    logo: '/logo+type.svg',
    docsDir: 'src/docs/vuepress',
    title: 'Gradle Shadow Plugin',
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
      '/publishing/',
      '/multi-project/',
      '/plugins/',
      '/changes/',
      '/about/'
    ]
  }
}
