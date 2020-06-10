

create table link_previews_t(
  site_c int not null,
  link_url_c varchar not null,
  downloaded_from_url_c varchar not null,
  downloaded_at_c timestamp not null,
  preview_type_c int not null,
  first_linked_by_c int not null,
  -- Righ now this is oEmbed request response json:
  content_json_c jsonb not null,

  -- The same link url can get downloaded many times, with different
  -- maxwidth=... params, for different device sizes / resolutions.
  -- For OpenGraph, downloaded_from_url_c is the same as link_url_c,
  -- but for oEmbed, it's different.
  constraint  linkpreviews_p primary key (site_c, downloaded_from_url_c),

  -- fk index: linkpreviews_i_firstlinkedby
  constraint linkpreviews_r_pps foreign key (site_c, first_linked_by_c)
      references users3 (site_id, user_id),

  constraint linkpreviews_c_linkurl_len check (
      length(link_url_c) between 5 and 500),

  constraint linkpreviews_c_downloadedfromurl_len check (
      length(downloaded_from_url_c) between 5 and 500),

  constraint linkpreviews_c_previewtype check (
      preview_type_c between 1 and 9),

  constraint linkpreviews_c_contentjson_len check (
      pg_column_size(content_json_c) between 5 and 5000)
);


create index linkpreviews_i_downloadedat on link_previews_t (downloaded_at_c);
create index linkpreviews_i_site_downlat on link_previews_t (site_c, downloaded_at_c);
create index linkpreviews_i_firstlinkedby on link_previews_t (site_c, first_linked_by_c);


create table links_t(
  site_c int not null,
  from_post_c int not null,
  -- There's no foreign key to link_previews_t, because maybe no preview has
  -- been downloaded yet (or maybe never — maybe broken external link).
  link_url_c varchar not null,
  added_at_c timestamp not null,
  added_by_c int not null,
  -- Exactly one of these:
  is_external_c boolean,
  to_post_c int,
  to_pp_c int,
  to_tag_c int,
  to_categoy_c int,

  constraint  links_p primary key (site_c, from_post_c, link_url_c),

  -- fk index: the primary key.
  constraint  links_frompost_r_posts foreign key (site_c, from_post_c)
      references posts3 (site_id, unique_post_id),

  -- No:
  -- fk index: links_i_linkurl
  -- constraint links_linkurl_r_linkpreviews foreign key (site_c, link_url_c)
  --    references link_previews_t (site_c, link_url_c),

  -- fk index: links_i_addedby
  constraint links_r_pps foreign key (site_c, added_by_c)
      references users3 (site_id, user_id),

  -- fk index: links_i_topost
  constraint links_topost_r_posts foreign key (site_c, to_post_c)
      references posts3 (site_id, unique_post_id),

  -- fk index: links_i_topp
  constraint links_topost_r_pps foreign key (site_c, to_pp_c)
      references users3 (site_id, user_id),

  -- fk idnex: links_i_totag
  -- constranit  tag refs tag_defs_t  — table not yet created

  -- fk index: links_i_tocategory
  constraint links_tocat_r_categories foreign key (site_c, to_categoy_c)
      references categories3 (site_id, id),

  -- Link url length constraint in link_previews_t.

  constraint links_c_isexternal_null_or_true check (
      is_external_c or (is_external_c is null)),

  constraint links_c_to_just_one check (
      num_nonnulls(is_external_c, to_post_c, to_pp_c, to_tag_c, to_categoy_c) = 1)
);


create index links_i_linkurl    on links_t (site_c, link_url_c);
create index links_i_addedby    on links_t (site_c, added_by_c);
create index links_i_topost     on links_t (site_c, to_post_c);
create index links_i_topp       on links_t (site_c, to_pp_c);
create index links_i_totag      on links_t (site_c, to_tag_c);
create index links_i_tocategory on links_t (site_c, to_categoy_c);

