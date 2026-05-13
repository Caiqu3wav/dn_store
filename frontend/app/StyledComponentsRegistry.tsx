"use client";

import React from "react";
import { useServerInsertedHTML } from "next/navigation";
import { ServerStyleSheet, StyleSheetManager } from "styled-components";


/**
 * Next.js App Router + styled-components SSR support.
 *
 * This registry ensures styled-components styles are properly collected on the server
 * and injected during hydration/refresh.
 */
export default function StyledComponentsRegistry({
  children,
}: {
  children: React.ReactNode;
}) {
  const sheet = React.useMemo(() => new ServerStyleSheet(), []);

  useServerInsertedHTML(() => {
    // styled-components SSR injection
    const styleTags = sheet.getStyleElement();
    return <>{styleTags}</>;
  });

  return <StyleSheetManager sheet={sheet.instance}>{children}</StyleSheetManager>;
}

